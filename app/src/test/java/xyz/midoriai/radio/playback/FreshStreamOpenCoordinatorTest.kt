package xyz.midoriai.radio.playback

import androidx.media3.common.Player
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.midoriai.radio.ui.screens.nowplaying.RadioPlaybackState

class FreshStreamOpenCoordinatorTest {
    @Test
    fun tryBeginOpen_admitsOnlyOneReplacementTransactionAtATime() {
        val coordinator = FreshStreamOpenCoordinator()

        assertEquals(1L, coordinator.tryBeginOpen())
        assertNull(coordinator.tryBeginOpen())

        coordinator.finishOpen()

        assertEquals(2L, coordinator.tryBeginOpen())
    }

    @Test
    fun canRunDelayedOpen_rejectsWorkInvalidatedByNewerSelection() {
        val coordinator = FreshStreamOpenCoordinator()
        val queuedGeneration = coordinator.currentGeneration()

        coordinator.invalidatePendingWork()

        assertFalse(
            coordinator.canRunDelayedOpen(
                queuedGeneration = queuedGeneration,
                playbackDesired = true,
                channelSwitchInProgress = false,
            ),
        )
    }

    @Test
    fun canRunDelayedOpen_rejectsRetryWhileChannelFadeIsInProgress() {
        val coordinator = FreshStreamOpenCoordinator()

        assertFalse(
            coordinator.canRunDelayedOpen(
                queuedGeneration = coordinator.currentGeneration(),
                playbackDesired = true,
                channelSwitchInProgress = true,
            ),
        )
    }

    @Test
    fun canRunDelayedOpen_rejectsWorkAfterPauseInvalidatesIt() {
        val coordinator = FreshStreamOpenCoordinator()
        val queuedGeneration = coordinator.currentGeneration()

        coordinator.invalidatePendingWork()

        assertFalse(
            coordinator.canRunDelayedOpen(
                queuedGeneration = queuedGeneration,
                playbackDesired = false,
                channelSwitchInProgress = false,
            ),
        )
    }

    @Test
    fun canRunDelayedOpen_acceptsCurrentRetryAfterAnOpenCompletes() {
        val coordinator = FreshStreamOpenCoordinator()
        val openGeneration = coordinator.tryBeginOpen()
        coordinator.finishOpen()

        assertTrue(
            coordinator.canRunDelayedOpen(
                queuedGeneration = openGeneration!!,
                playbackDesired = true,
                channelSwitchInProgress = false,
            ),
        )
    }

    @Test
    fun resolveFreshStreamSelection_usesLatestChannelAndPendingQualityAtAdmission() {
        assertEquals(
            FreshStreamSelection(channel = "focus", quality = "high"),
            resolveFreshStreamSelection(
                selectedChannel = " Focus ",
                activeQuality = "low",
                pendingQuality = "high",
            ),
        )
    }

    @Test
    fun resolveFreshStreamSelection_usesActiveQualityWithoutPendingChange() {
        assertEquals(
            FreshStreamSelection(channel = "all", quality = "medium"),
            resolveFreshStreamSelection(
                selectedChannel = " ",
                activeQuality = "medium",
                pendingQuality = null,
            ),
        )
    }

    @Test
    fun rapidChannelAndQualityChanges_resolveOnlyTheLatestSelectionAtOpenAdmission() {
        val coordinator = FreshStreamOpenCoordinator()
        val staleRetryGeneration = coordinator.currentGeneration()

        coordinator.invalidatePendingWork()
        coordinator.invalidatePendingWork()
        val admittedGeneration = coordinator.tryBeginOpen()

        assertFalse(
            coordinator.canRunDelayedOpen(
                queuedGeneration = staleRetryGeneration,
                playbackDesired = true,
                channelSwitchInProgress = false,
            ),
        )
        assertEquals(3L, admittedGeneration)
        assertEquals(
            FreshStreamSelection(channel = "ambient", quality = "high"),
            resolveFreshStreamSelection(
                selectedChannel = " ambient ",
                activeQuality = "low",
                pendingQuality = "high",
            ),
        )
    }

    @Test
    fun channelSwitchVolumeRestoration_belongsOnlyToTheLatestSwitch() {
        val coordinator = FreshStreamOpenCoordinator()
        val olderSwitch = coordinator.beginChannelSwitch()
        val newerSwitch = coordinator.beginChannelSwitch()

        assertFalse(coordinator.isCurrentChannelSwitch(olderSwitch))
        assertTrue(coordinator.isCurrentChannelSwitch(newerSwitch))

        coordinator.invalidateChannelSwitch()

        assertFalse(coordinator.isCurrentChannelSwitch(newerSwitch))
    }

    @Test
    fun runOpenTransaction_rejectsReentrantSourceReplacement() {
        val coordinator = FreshStreamOpenCoordinator()
        var nestedResult: FreshStreamOpenResult? = null

        val outerResult = coordinator.runOpenTransaction {
            nestedResult = coordinator.runOpenTransaction { error("must not run") }
        }

        assertEquals(FreshStreamOpenResult.Opened, outerResult)
        assertEquals(FreshStreamOpenResult.Rejected, nestedResult)
    }

    @Test
    fun serviceOpen_rejectionPreservesPendingReconnectUntilAnOpenIsAdmitted() {
        val coordinator = FreshStreamOpenCoordinator()
        var pendingReconnect = true
        var rejectedResult: FreshStreamOpenResult? = null

        runFreshStreamOpenTransaction(
            coordinator = coordinator,
            cancelPendingReconnect = { pendingReconnect = false },
        ) {
            pendingReconnect = true
            rejectedResult = runFreshStreamOpenTransaction(
                coordinator = coordinator,
                cancelPendingReconnect = { pendingReconnect = false },
            ) { error("rejected open must not run") }
            assertTrue(pendingReconnect)
        }

        assertEquals(FreshStreamOpenResult.Rejected, rejectedResult)
        assertTrue(pendingReconnect)

        runFreshStreamOpenTransaction(
            coordinator = coordinator,
            cancelPendingReconnect = { pendingReconnect = false },
        ) {}

        assertFalse(pendingReconnect)
    }

    @Test
    fun selectionSupersession_invalidatesRetryAndRestoresVolume() {
        val coordinator = FreshStreamOpenCoordinator()
        val queuedRetry = coordinator.currentGeneration()
        val volume = PlayerVolume(0f)
        val player = volumePlayer(volume)
        val effects = playerEffects(player)

        coordinator.supersedeForSelection(effects::restoreNormalVolume)

        assertEquals(1f, player.volume)
        assertFalse(
            coordinator.canRunDelayedOpen(
                queuedGeneration = queuedRetry,
                playbackDesired = true,
                channelSwitchInProgress = false,
            ),
        )
    }

    @Test
    fun switchCompletion_restoresVolumeOnCancellationButNotAfterSupersession() {
        val coordinator = FreshStreamOpenCoordinator()
        val volume = PlayerVolume(0f)
        val player = volumePlayer(volume)
        val effects = playerEffects(player)
        val cancelledSwitch = coordinator.beginChannelSwitch()

        effects.finishChannelSwitch(
            coordinator = coordinator,
            switchGeneration = cancelledSwitch,
            openResult = null,
            playbackDesired = true,
        )
        assertEquals(1f, player.volume)

        player.volume = 0f
        val supersededSwitch = coordinator.beginChannelSwitch()
        coordinator.supersedeForSelection(effects::restoreNormalVolume)
        effects.finishChannelSwitch(
            coordinator = coordinator,
            switchGeneration = supersededSwitch,
            openResult = null,
            playbackDesired = true,
        )

        assertEquals(1f, player.volume)
    }

    @Test
    fun rejectedSwitchOpen_restoresPlayerVolumeAndConnectingState() {
        val coordinator = FreshStreamOpenCoordinator()
        val volume = PlayerVolume(0f)
        val player = volumePlayer(volume)
        var state: RadioPlaybackState = RadioPlaybackState.SwitchingChannel("Focus")
        val effects = playerEffects(player) { state = it }

        coordinator.runOpenTransaction {
            val switchGeneration = coordinator.beginChannelSwitch()
            val rejectedResult = coordinator.runOpenTransaction { error("must not run") }

            effects.finishChannelSwitch(
                coordinator = coordinator,
                switchGeneration = switchGeneration,
                openResult = rejectedResult,
                playbackDesired = true,
            )
        }

        assertEquals(1f, player.volume)
        assertEquals(RadioPlaybackState.Connecting, state)
    }

    @Test
    fun failedOpenTransaction_restoresActualPlayerVolumeAndReleasesAdmission() {
        val coordinator = FreshStreamOpenCoordinator()
        val volume = PlayerVolume(0f)
        val player = volumePlayer(volume)
        val effects = playerEffects(player)

        assertThrows(IllegalStateException::class.java) {
            coordinator.runOpenTransaction(
                onFinished = effects::restoreNormalVolume,
            ) {
                player.volume = 0.2f
                error("open failed")
            }
        }

        assertEquals(1f, player.volume)
        assertEquals(2L, coordinator.tryBeginOpen())
    }

    @Test
    fun rejectedOpen_cannotContinueChannelSwitch() {
        assertFalse(
            shouldContinueChannelSwitch(
                openResult = FreshStreamOpenResult.Rejected,
                isCurrentSwitch = true,
                playbackDesired = true,
            ),
        )
        assertTrue(
            shouldContinueChannelSwitch(
                openResult = FreshStreamOpenResult.Opened,
                isCurrentSwitch = true,
                playbackDesired = true,
            ),
        )
    }

    private data class PlayerVolume(var value: Float)

    private fun playerEffects(
        player: Player,
        updatePlaybackState: (RadioPlaybackState) -> Unit = {},
    ): FreshStreamPlayerEffects {
        return FreshStreamPlayerEffects(
            player = player,
            normalVolume = 1f,
            updatePlaybackState = updatePlaybackState,
        )
    }

    private fun volumePlayer(volume: PlayerVolume): Player {
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getVolume" -> volume.value
                "setVolume" -> {
                    volume.value = args?.firstOrNull() as Float
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "VolumePlayer"
                else -> defaultValue(method)
            }
        } as Player
    }

    private fun defaultValue(method: Method): Any? {
        return when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Void.TYPE -> null
            else -> null
        }
    }
}
