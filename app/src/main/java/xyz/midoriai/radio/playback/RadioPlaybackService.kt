package xyz.midoriai.radio.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.midoriai.radio.MainActivity
import xyz.midoriai.radio.R
import xyz.midoriai.radio.radioapi.ArtPayload
import xyz.midoriai.radio.radioapi.CurrentPayload
import xyz.midoriai.radio.radioapi.OkHttpRadioApi
import xyz.midoriai.radio.radioapi.RadioApi
import xyz.midoriai.radio.radioapi.RadioApiDefaults
import xyz.midoriai.radio.radioapi.RadioApiResult
import xyz.midoriai.radio.radioapi.buildStreamUrl
import xyz.midoriai.radio.radioapi.normalizeQuality
import xyz.midoriai.radio.settings.RadioSettingsRepository
import xyz.midoriai.radio.ui.screens.nowplaying.RadioPlaybackState

@UnstableApi
class RadioPlaybackService : MediaLibraryService() {
    private val playingMetadataPollIntervalMs = 5000L
    private val idleMetadataPollIntervalMs = 20000L
    private val channelRefreshIntervalMs = 60000L
    private val channelSwitchFadeDurationMs = 220L
    private val adjacentArtPrefetchCooldownMs = 15000L
    private val playerVolumeNormal = 1f

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsRepository by lazy { RadioSettingsRepository(applicationContext) }
    private val api: RadioApi = OkHttpRadioApi()

    private val isDebugBuild: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(this)
            .build()
            .also { exo ->
                exo.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                exo.repeatMode = Player.REPEAT_MODE_OFF
                exo.volume = playerVolumeNormal
                exo.setHandleAudioBecomingNoisy(true)
                exo.addListener(
                    RadioPlaybackPlayerEventListener(
                        playbackDesired = { playbackDesired },
                        currentPlaybackState = { exo.playbackState },
                        onPlayerFailure = ::handlePlayerError,
                        onPlaybackState = { recoveryController.onPlaybackStateChanged(it) },
                        onStopped = ::handlePlayerStopped,
                        onMediaItem = ::handleMediaItemTransition,
                    ),
                )
            }
    }

    private val sessionPlayer: Player by lazy {
        mediaSessionStreamOpening.wrapPlayer(
            player = player,
            onPlay = this::play,
            onPause = this::pausePlayback,
            onSelectAdjacentChannel = this::selectAdjacentChannel,
        )
    }

    private val mediaSessionStreamOpening by lazy {
        MediaSessionStreamOpening(
            resolveRequestedChannel = this::resolveRequestedChannel,
            effectiveQualityForNextOpen = this::effectiveQualityForNextOpen,
            buildPlaybackItems = this::buildPlaybackItemsForChannel,
            openSelectedStream = this::openSessionSelection,
        )
    }

    private val freshStreamPlayerEffects by lazy {
        FreshStreamPlayerEffects(
            player = player,
            normalVolume = playerVolumeNormal,
            updatePlaybackState = { state -> _playbackState.value = state },
        )
    }

    private val freshStreamPlayerInstaller by lazy { FreshStreamPlayerInstaller(player) }

    private var mediaSession: MediaLibrarySession? = null

    private val _playbackState = MutableStateFlow<RadioPlaybackState>(RadioPlaybackState.Idle)
    private val _currentTrack = MutableStateFlow<CurrentPayload?>(null)
    private val _art = MutableStateFlow<ArtPayload?>(null)
    private val _channels = MutableStateFlow(listOf("all"))
    private val _selectedChannel = MutableStateFlow("all")
    private val _selectedQuality = MutableStateFlow("medium")
    private val _activeQuality = MutableStateFlow("medium")
    private val _pendingQuality = MutableStateFlow<String?>(null)

    private val artByChannel: MutableMap<String, ArtPayload> = mutableMapOf()
    private val artFetchInFlight: MutableSet<String> = mutableSetOf()
    private val artFetchAtMsByChannel: MutableMap<String, Long> = mutableMapOf()

    private var playbackDesired: Boolean = false
    private var channelSwitchJob: Job? = null
    private var pendingChannelSelection: String? = null
    private var metadataPollJob: Job? = null
    private var channelPollJob: Job? = null
    private val freshStreamOpenCoordinator = FreshStreamOpenCoordinator()
    private val recoveryController by lazy {
        RadioPlaybackRecoveryController(
            coordinator = freshStreamOpenCoordinator,
            scheduler = RecoveryRetryScheduler { delayMs, action ->
                val job = serviceScope.launch {
                    delay(delayMs)
                    action()
                }
                RecoveryRetryHandle(job::cancel)
            },
            playbackDesired = { playbackDesired },
            playbackState = { player.playbackState },
            playWhenReady = { player.playWhenReady },
            isPlaying = { player.isPlaying },
            sourceIsCurrent = {
                player.currentMediaItem?.mediaId == normalizePersistedChannel(_selectedChannel.value) &&
                    lastPlaylistQuality == _activeQuality.value
            },
            channelSwitchInProgress = ::isChannelSwitchInProgress,
            openFreshStream = ::openFreshStream,
            updatePlaybackState = { _playbackState.value = it },
        )
    }
    private var lastObservedChannel: String? = null
    private var lastObservedQuality: String? = null
    private var lastPlaylistChannel: String? = null
    private var lastPlaylistQuality: String = "medium"
    private var lastPublishedMetadataFingerprint: String? = null
    private var lastPublishedSnapshot: RadioSessionSnapshot? = null

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaLibrarySession.Builder(this, sessionPlayer, SessionCallback())
            .setSessionActivity(buildSessionActivity())
            .build()

        observeSessionStatePublishing()
        observeSelectedSettings()
        startMetadataPolling()
        startChannelPolling()
        publishSessionSnapshotAndMetadata()
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        clearReconnectState()
        freshStreamOpenCoordinator.invalidateChannelSwitch()
        channelSwitchJob?.cancel()
        channelSwitchJob = null
        metadataPollJob?.cancel()
        metadataPollJob = null
        channelPollJob?.cancel()
        channelPollJob = null

        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null

        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeSessionStatePublishing() {
        serviceScope.launch { _playbackState.collect { publishSessionSnapshotAndMetadata() } }
        serviceScope.launch { _currentTrack.collect { publishSessionSnapshotAndMetadata() } }
        serviceScope.launch { _art.collect { publishSessionSnapshotAndMetadata() } }
        serviceScope.launch { _channels.collect { publishSessionSnapshotAndMetadata() } }
        serviceScope.launch { _selectedChannel.collect { publishSessionSnapshotAndMetadata() } }
        serviceScope.launch { _selectedQuality.collect { publishSessionSnapshotAndMetadata() } }
        serviceScope.launch { _pendingQuality.collect { publishSessionSnapshotAndMetadata() } }
    }

    private fun observeSelectedSettings() {
        serviceScope.launch {
            settingsRepository.channel.collect { channel ->
                val normalized = normalizePersistedChannel(channel)
                _selectedChannel.value = normalized
                _channels.update { ensureAllChannel(it + normalized) }

                if (lastObservedChannel == null) {
                    lastObservedChannel = normalized
                    syncPlayerPlaylist(normalized, forceRebuild = true)
                    publishCachedArtForSelectedChannel(normalized)
                    serviceScope.launch {
                        refreshSelectedAndAdjacentArt(
                            selected = normalized,
                            forceSelectedRefresh = true,
                        )
                    }
                    return@collect
                }

                lastObservedChannel = normalized
                publishCachedArtForSelectedChannel(normalized)

                if (playbackDesired) {
                    if (player.currentMediaItem?.mediaId == normalized) {
                        recoveryController.resetErrorAttempts()
                        serviceScope.launch {
                            pollCurrentOnce(channelOverride = normalized)
                            pollArtOnce(channelOverride = normalized)
                        }
                    } else {
                        if (!isChannelSwitchInProgress()) {
                            switchChannelWithFade(normalized)
                        }
                    }
                } else {
                    syncPlayerPlaylist(normalized)
                    serviceScope.launch {
                        refreshSelectedAndAdjacentArt(
                            selected = normalized,
                            forceSelectedRefresh = true,
                        )
                    }
                }
            }
        }

        serviceScope.launch {
            settingsRepository.quality.collect { quality ->
                val normalized = normalizeQuality(quality)
                _selectedQuality.value = normalized

                if (lastObservedQuality == null) {
                    lastObservedQuality = normalized
                    _activeQuality.value = normalized
                    syncPlayerPlaylist(_selectedChannel.value, forceRebuild = true)
                    return@collect
                }

                lastObservedQuality = normalized
                if (playbackDesired) {
                    _pendingQuality.value = normalized
                } else {
                    _activeQuality.value = normalized
                    _pendingQuality.value = null
                    syncPlayerPlaylist(_selectedChannel.value, forceRebuild = true)
                }
            }
        }
    }

    private fun play() {
        if (playbackDesired) {
            if (!player.playWhenReady) {
                if (player.playbackState == Player.STATE_IDLE) {
                    recoveryController.retryFailedOpen(openFreshStream())
                } else {
                    player.playWhenReady = true
                }
            }
            return
        }

        playbackDesired = true
        recoveryController.resetErrorAttempts()
        if (player.playbackState == Player.STATE_IDLE) {
            recoveryController.retryFailedOpen(openFreshStream())
        } else {
            player.volume = playerVolumeNormal
            player.playWhenReady = true
        }
    }

    private fun pausePlayback() {
        playbackDesired = false
        freshStreamOpenCoordinator.invalidatePendingWork()
        freshStreamOpenCoordinator.invalidateChannelSwitch()
        clearReconnectState()
        channelSwitchJob?.cancel()
        channelSwitchJob = null
        pendingChannelSelection = null

        player.playWhenReady = false
        player.stop()
        player.volume = playerVolumeNormal
        _playbackState.value = RadioPlaybackState.Stopped
    }

    private fun selectAdjacentChannel(direction: Int) {
        dispatchAdjacentChannelSelection(
            selectedChannel = _selectedChannel.value,
            pendingChannelSelection = pendingChannelSelection,
            availableChannels = _channels.value,
            direction = direction,
            selectChannel = ::setChannel,
        )
    }

    private fun setQuality(quality: String) {
        val normalizedQuality = normalizeQuality(quality)
        _selectedQuality.value = normalizedQuality
        if (playbackDesired) {
            _pendingQuality.value = normalizedQuality
            freshStreamOpenCoordinator.invalidatePendingWork()
            recoveryController.cancelPendingRecovery()
            pendingChannelSelection = null
            if (
                !isHealthyPlayback(
                    playbackState = player.playbackState,
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                )
            ) {
                recoveryController.onPlayerError()
            }
        } else {
            _activeQuality.value = normalizedQuality
            _pendingQuality.value = null
            syncPlayerPlaylist(_selectedChannel.value, forceRebuild = true)
        }
        serviceScope.launch {
            settingsRepository.setQuality(normalizedQuality)
        }
    }

    private fun setChannel(channel: String) {
        val normalizedChannel = normalizePersistedChannel(channel)
        prepareForChannelSelection()
        if (playbackDesired && player.currentMediaItem?.mediaId != normalizedChannel) {
            pendingChannelSelection = normalizedChannel
            switchChannelWithFade(normalizedChannel)
        } else {
            commitChannelSelection(normalizedChannel)
            if (
                playbackDesired &&
                !isHealthyPlayback(
                    playbackState = player.playbackState,
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                )
            ) {
                recoveryController.onPlayerError()
            }
            if (!playbackDesired) {
                syncPlayerPlaylist(normalizedChannel, forceRebuild = true)
            }
        }
    }

    private fun prepareForChannelSelection() {
        recoveryController.cancelPendingRecovery()
        freshStreamOpenCoordinator.supersedeForSelection {
            freshStreamPlayerEffects.restoreNormalVolume()
        }
        channelSwitchJob?.cancel()
        channelSwitchJob = null
    }

    private fun openSessionSelection(channel: String): FreshStreamOpenResult {
        val normalizedChannel = normalizePersistedChannel(channel)
        val targetQuality = effectiveQualityForNextOpen()
        prepareForChannelSelection()
        pendingChannelSelection = normalizedChannel
        val result = openSessionSelectionOnce(normalizedChannel)
        recoveryController.retryFailedOpen(
            result = result,
            retryOpen = {
                openSessionSelectionOnce(normalizedChannel).also {
                    if (it == FreshStreamOpenResult.Opened) pendingChannelSelection = null
                }
            },
            isStillCurrent = { pendingChannelSelection == normalizedChannel },
            recoveryTargetSatisfied = {
                currentSourceMatches(normalizedChannel, targetQuality)
            },
        )
        if (result != FreshStreamOpenResult.Failed) {
            pendingChannelSelection = null
        }
        return result
    }

    private fun openSessionSelectionOnce(channel: String): FreshStreamOpenResult {
        return openAndCommitChannelSelection(
            channel = channel,
            openSelectedStream = { normalizedChannel, onOpened ->
                openFreshStream(
                    playWhenReadyAfterOpen = playbackDesired,
                    channelForOpen = normalizedChannel,
                    onOpenSucceeded = onOpened,
                )
            },
            commitSelection = ::applySessionSelection,
        )
    }

    private fun applySessionSelection(channel: String) {
        val normalizedChannel = normalizePersistedChannel(channel)
        freshStreamOpenCoordinator.invalidateChannelSwitch()
        freshStreamPlayerEffects.restoreNormalVolume()
        channelSwitchJob?.cancel()
        channelSwitchJob = null
        commitChannelSelection(normalizedChannel)
    }

    private fun openExplicitChannelSelection(
        channel: String,
        restoreVolumeAfterOpen: Boolean,
        playWhenReadyAfterOpen: Boolean,
    ): FreshStreamOpenResult {
        return openAndCommitChannelSelection(
            channel = channel,
            openSelectedStream = { normalizedChannel, onOpened ->
                openFreshStream(
                    restoreVolumeAfterOpen = restoreVolumeAfterOpen,
                    playWhenReadyAfterOpen = playWhenReadyAfterOpen,
                    channelForOpen = normalizedChannel,
                    onOpenSucceeded = onOpened,
                )
            },
            commitSelection = ::commitChannelSelection,
        )
    }

    private fun commitChannelSelection(normalizedChannel: String) {
        val previousChannels = ensureAllChannel(_channels.value)
        _selectedChannel.value = normalizedChannel
        _channels.update { ensureAllChannel(it + normalizedChannel) }
        if (_channels.value != previousChannels) {
            notifyBrowseRootChildrenChanged()
        }
        publishCachedArtForSelectedChannel(normalizedChannel)
        serviceScope.launch {
            settingsRepository.setChannel(normalizedChannel)
            refreshSelectedAndAdjacentArt(
                selected = normalizedChannel,
                forceSelectedRefresh = false,
            )
        }
    }

    private fun switchChannelWithFade(channel: String) {
        recoveryController.cancelPendingRetry()
        val switchGeneration = freshStreamOpenCoordinator.beginChannelSwitch()
        channelSwitchJob?.cancel()
        channelSwitchJob = serviceScope.launch {
            var openResult: FreshStreamOpenResult? = null
            try {
                if (!playbackDesired) {
                    return@launch
                }

                recoveryController.resetErrorAttempts()
                _playbackState.value = RadioPlaybackState.SwitchingChannel(toChannelLabel(channel))

                fadePlayerVolume(0f, channelSwitchFadeDurationMs)
                if (
                    !freshStreamOpenCoordinator.isCurrentChannelSwitch(switchGeneration) ||
                    !playbackDesired
                ) {
                    return@launch
                }
                openResult = openExplicitChannelSelection(
                    channel = channel,
                    restoreVolumeAfterOpen = false,
                    playWhenReadyAfterOpen = playbackDesired,
                )
                if (
                    openResult == FreshStreamOpenResult.Failed &&
                    freshStreamOpenCoordinator.isCurrentChannelSwitch(switchGeneration) &&
                    playbackDesired
                ) {
                    val targetQuality = effectiveQualityForNextOpen()
                    recoveryController.replacePendingRetryWithFailedOpen(
                        result = openResult,
                        retryOpen = {
                            openExplicitChannelSelection(
                                channel = channel,
                                restoreVolumeAfterOpen = true,
                                playWhenReadyAfterOpen = playbackDesired,
                            ).also {
                                if (it == FreshStreamOpenResult.Opened) pendingChannelSelection = null
                            }
                        },
                        isStillCurrent = { pendingChannelSelection == channel },
                        recoveryTargetSatisfied = {
                            currentSourceMatches(channel, targetQuality)
                        },
                    )
                }
                if (
                    !shouldContinueChannelSwitch(
                        openResult = openResult,
                        isCurrentSwitch = freshStreamOpenCoordinator.isCurrentChannelSwitch(switchGeneration),
                        playbackDesired = playbackDesired,
                    )
                ) {
                    return@launch
                }
                fadePlayerVolume(playerVolumeNormal, channelSwitchFadeDurationMs)

                pollCurrentOnce(channelOverride = _selectedChannel.value)
                pollArtOnce(channelOverride = _selectedChannel.value)
            } finally {
                if (
                    pendingChannelSelection == channel &&
                    openResult != FreshStreamOpenResult.Failed
                ) {
                    pendingChannelSelection = null
                }
                freshStreamPlayerEffects.finishChannelSwitch(
                    coordinator = freshStreamOpenCoordinator,
                    switchGeneration = switchGeneration,
                    openResult = openResult,
                    playbackDesired = playbackDesired,
                )
            }
        }
    }

    private fun openFreshStream(
        restoreVolumeAfterOpen: Boolean = true,
        playWhenReadyAfterOpen: Boolean = true,
        channelForOpen: String? = null,
        onOpenSucceeded: () -> Unit = {},
    ): FreshStreamOpenResult {
        val result = openFreshStreamPlayerSource(
            coordinator = freshStreamOpenCoordinator,
            cancelPendingReconnect = recoveryController::cancelRetryDelayForOpen,
            onFinished = {
                if (restoreVolumeAfterOpen) {
                    freshStreamPlayerEffects.restoreNormalVolume()
                }
            },
        ) { openGeneration ->
            val previousPlaybackState = _playbackState.value
            val previousActiveQuality = _activeQuality.value
            val previousPendingQuality = _pendingQuality.value
            val previousPlaylistChannel = lastPlaylistChannel
            val previousPlaylistQuality = lastPlaylistQuality
            val selection = resolveFreshStreamSelection(
                selectedChannel = channelForOpen ?: _selectedChannel.value,
                activeQuality = _activeQuality.value,
                pendingQuality = _pendingQuality.value,
            )
            val channel = selection.channel
            val quality = commitQualityForOpen(selection.quality)
            val streamUrl = buildStreamUrl(
                baseUrl = RadioApiDefaults.DEFAULT_BASE_URL,
                channel = channel,
                quality = quality,
            )

            if (isDebugBuild) {
                Log.d(
                    "MidoriAIRadio",
                    "Radio endpoints: stream=$streamUrl current=/radio/v1/current art=/radio/v1/art artImage=/radio/v1/art/image health=/health reconnectDelaysMs=$errorReconnectDelaysMs openGeneration=$openGeneration",
                )
            }

            freshStreamPlayerInstaller.install(
                replaceSource = {
                    _playbackState.value = RadioPlaybackState.Connecting
                    syncPlayerPlaylist(
                        channel,
                        forceRebuild = lastPlaylistQuality != quality,
                        refreshMetadata = false,
                    )
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekToDefaultPosition()
                    }
                },
                playWhenReady = playWhenReadyAfterOpen,
                onFailed = {
                    _activeQuality.value = previousActiveQuality
                    _pendingQuality.value = previousPendingQuality
                    lastPlaylistChannel = previousPlaylistChannel
                    lastPlaylistQuality = previousPlaylistQuality
                    _playbackState.value = if (
                        previousPlaybackState is RadioPlaybackState.SwitchingChannel
                    ) {
                        RadioPlaybackState.Connecting
                    } else {
                        previousPlaybackState
                    }
                },
            )
        }
        return completeFreshStreamOpen(result) {
            recoveryController.sourceOpened()
            onOpenSucceeded()
            runCatching(::syncCurrentMediaItemMetadata)
        }
    }

    private fun clearReconnectState() {
        recoveryController.cancelPendingRecovery()
    }

    private fun isChannelSwitchInProgress(): Boolean = channelSwitchJob?.isActive == true

    private fun currentSourceMatches(channel: String, quality: String): Boolean {
        return player.currentMediaItem?.mediaId == normalizePersistedChannel(channel) &&
            lastPlaylistQuality == normalizeQuality(quality)
    }

    private fun commitQualityForOpen(quality: String): String {
        if (!_pendingQuality.value.isNullOrBlank()) {
            _activeQuality.value = quality
            _pendingQuality.value = null
            return quality
        }

        if (quality != _activeQuality.value) {
            _activeQuality.value = quality
        }

        return quality
    }

    private fun effectiveQualityForNextOpen(): String {
        return resolveFreshStreamSelection(
            selectedChannel = _selectedChannel.value,
            activeQuality = _activeQuality.value,
            pendingQuality = _pendingQuality.value,
        ).quality
    }

    private fun syncPlayerPlaylist(
        selectedChannel: String,
        forceRebuild: Boolean = false,
        refreshMetadata: Boolean = true,
    ) {
        val normalizedSelected = normalizePersistedChannel(selectedChannel)
        val availableChannels = ensureAllChannel(_channels.value + normalizedSelected)
        if (_channels.value != availableChannels) {
            _channels.value = availableChannels
        }

        val activeQuality = normalizeQuality(_activeQuality.value)
        val needsRebuild = forceRebuild ||
            lastPlaylistChannel != normalizedSelected ||
            lastPlaylistQuality != activeQuality ||
            player.mediaItemCount != 1

        if (needsRebuild) {
            recoveryController.invalidateInstalledSource()
            player.setMediaItem(buildMediaItem(channel = normalizedSelected, quality = activeQuality))
            lastPlaylistChannel = normalizedSelected
            lastPlaylistQuality = activeQuality
            lastPublishedMetadataFingerprint = null

            if (!playbackDesired) {
                player.playWhenReady = false
            }
        }

        if (refreshMetadata) {
            syncCurrentMediaItemMetadata()
        }
    }

    private fun buildLibraryRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(RADIO_BROWSE_ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(getString(R.string.app_name))
                    .setDisplayTitle(getString(R.string.app_name))
                    .setArtist(getString(R.string.auto_browse_root_subtitle))
                    .build(),
            )
            .build()
    }

    private fun buildLibraryChannelItem(channel: String): MediaItem {
        val normalizedChannel = normalizePersistedChannel(channel)
        val channelTitle = toChannelDisplayName(normalizedChannel)
        val metadataBuilder = MediaMetadata.Builder()
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setAlbumTitle(getString(R.string.app_name))
            .setTitle(channelTitle)
            .setDisplayTitle(channelTitle)
            .setArtist(toChannelSubtitle(normalizedChannel))

        // Artwork is absent until the fetch completes or when the API reports no art.
        artByChannel[normalizedChannel]
            ?.artUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { metadataBuilder.setArtworkUri(it.toUri()) }

        return MediaItem.Builder()
            .setMediaId(toBrowseChannelMediaId(normalizedChannel))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun buildPlaybackItemsForChannel(
        selectedChannel: String,
        quality: String,
    ): List<MediaItem> {
        return listOf(buildMediaItem(channel = selectedChannel, quality = quality))
    }

    private fun buildSearchResultItems(query: String): List<MediaItem> {
        val channel = resolveChannelForSearch(
            query = query,
            availableChannels = _channels.value,
        )
        return listOf(buildLibraryChannelItem(channel))
    }

    private fun resolveRequestedChannel(mediaItems: List<MediaItem>): String {
        val requestedItem = mediaItems.firstOrNull() ?: return "all"
        val requestedMediaId = requestedItem.mediaId.trim()
        val requestedQuery = requestedItem.requestMetadata.searchQuery

        channelFromBrowseMediaId(requestedMediaId)?.let { return it }

        if (requestedMediaId.isNotBlank()) {
            val normalizedMediaId = normalizePersistedChannel(requestedMediaId)
            if (normalizedMediaId == "all" || normalizedMediaId in _channels.value) {
                return normalizedMediaId
            }
        }

        return resolveChannelForSearch(
            query = requestedQuery,
            availableChannels = _channels.value,
        )
    }

    private fun pagedMediaItems(
        items: List<MediaItem>,
        page: Int,
        pageSize: Int,
    ): List<MediaItem> {
        if (page < 0 || pageSize <= 0) {
            return emptyList()
        }

        val fromIndexLong = page.toLong() * pageSize.toLong()
        if (fromIndexLong >= items.size || fromIndexLong < 0L) {
            return emptyList()
        }

        val fromIndex = fromIndexLong.toInt()
        val toIndex = (fromIndexLong + pageSize.toLong())
            .coerceAtMost(items.size.toLong())
            .toInt()
        return items.subList(fromIndex, toIndex)
    }

    private fun buildMediaItem(
        channel: String,
        quality: String,
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(normalizePersistedChannel(channel))
            .setUri(
                buildStreamUrl(
                    baseUrl = RadioApiDefaults.DEFAULT_BASE_URL,
                    channel = channel,
                    quality = quality,
                ),
            )
            .setMediaMetadata(buildMediaMetadata(channel))
            .build()
    }

    private fun buildMediaMetadata(channel: String): MediaMetadata {
        val normalizedChannel = normalizePersistedChannel(channel)
        val selectedChannel = normalizePersistedChannel(_selectedChannel.value)

        val builder = MediaMetadata.Builder()
            .setIsPlayable(true)
            .setAlbumTitle(getString(R.string.app_name))

        if (normalizedChannel == selectedChannel) {
            val presentation = resolveRadioPresentationState(
                selectedChannel = selectedChannel,
                currentTrack = _currentTrack.value,
                art = _art.value,
            )
            builder
                .setTitle(presentation.trackTitle)
                .setArtist(presentation.channelSubtitle)
                .setDisplayTitle(presentation.trackTitle)
            presentation.artUrl?.takeIf { it.isNotBlank() }?.let { artUrl ->
                builder.setArtworkUri(
                    buildSessionArtworkUrl(
                        artUrl = artUrl,
                        trackId = presentation.trackId,
                    ).toUri(),
                )
            }
        } else {
            builder
                .setTitle(toChannelDisplayName(normalizedChannel))
                .setArtist(getString(R.string.app_name))
                .setDisplayTitle(toChannelDisplayName(normalizedChannel))
        }

        return builder.build()
    }

    private fun syncCurrentMediaItemMetadata() {
        val currentItem = player.currentMediaItem ?: return
        val currentChannel = currentItem.mediaId.takeIf { it.isNotBlank() }
            ?: normalizePersistedChannel(_selectedChannel.value)
        val currentIndex = player.currentMediaItemIndex.takeIf { it >= 0 }
            ?: 0.takeIf { player.mediaItemCount == 1 }
            ?: return
        if (currentIndex !in 0 until player.mediaItemCount) {
            return
        }

        val fingerprint = buildMetadataFingerprint(currentChannel)
        if (fingerprint == lastPublishedMetadataFingerprint) {
            return
        }

        val updatedItem = currentItem.buildUpon()
            .setMediaMetadata(buildMediaMetadata(currentChannel))
            .build()
        player.replaceMediaItem(currentIndex, updatedItem)
        lastPublishedMetadataFingerprint = fingerprint
    }

    private fun buildMetadataFingerprint(channel: String): String {
        val normalizedChannel = normalizePersistedChannel(channel)
        val presentation = if (normalizedChannel == normalizePersistedChannel(_selectedChannel.value)) {
            resolveRadioPresentationState(
                selectedChannel = normalizedChannel,
                currentTrack = _currentTrack.value,
                art = _art.value,
            )
        } else {
            null
        }
        val metadataArtworkUrl = presentation?.artUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { artUrl ->
                buildSessionArtworkUrl(
                    artUrl = artUrl,
                    trackId = presentation.trackId,
                )
            }

        return listOf(
            normalizedChannel,
            lastPlaylistQuality,
            presentation?.trackId.orEmpty(),
            presentation?.trackTitle.orEmpty(),
            presentation?.channelSubtitle.orEmpty(),
            metadataArtworkUrl.orEmpty(),
        ).joinToString("|")
    }

    private fun startMetadataPolling() {
        if (metadataPollJob?.isActive == true) {
            return
        }

        metadataPollJob = serviceScope.launch {
            while (isActive) {
                pollCurrentOnce()
                pollArtOnce()
                val delayMs = if (playbackDesired) {
                    playingMetadataPollIntervalMs
                } else {
                    idleMetadataPollIntervalMs
                }
                delay(delayMs)
            }
        }
    }

    private fun startChannelPolling() {
        if (channelPollJob?.isActive == true) {
            return
        }

        channelPollJob = serviceScope.launch {
            while (isActive) {
                refreshChannels()
                delay(channelRefreshIntervalMs)
            }
        }
    }

    private suspend fun refreshChannels() {
        val previousChannels = ensureAllChannel(_channels.value)
        var resolvedChannels = ensureAllChannel(_channels.value)

        try {
            when (val result = api.fetchChannels()) {
                is RadioApiResult.Success -> {
                    val merged = result.data.channels
                        .map { normalizePersistedChannel(it.name) }
                        .filter { it.isNotBlank() && it != "all" }
                        .distinct()

                    resolvedChannels = ensureAllChannel(merged + _selectedChannel.value)
                    _channels.value = resolvedChannels
                }

                is RadioApiResult.Failure -> {
                    resolvedChannels = ensureAllChannel(_channels.value + _selectedChannel.value)
                    _channels.value = resolvedChannels
                }
            }
        } catch (_: Exception) {
            resolvedChannels = ensureAllChannel(_channels.value + _selectedChannel.value)
            _channels.value = resolvedChannels
        }

        syncPlayerPlaylist(_selectedChannel.value)
        if (resolvedChannels != previousChannels) {
            notifyBrowseRootChildrenChanged()
        }
        pruneArtCache(resolvedChannels)
        publishCachedArtForSelectedChannel(_selectedChannel.value)
        try {
            refreshSelectedAndAdjacentArt(
                selected = _selectedChannel.value,
                forceSelectedRefresh = false,
            )
        } catch (exc: CancellationException) {
            throw exc
        } catch (_: Exception) {
            // Preserve existing channel data even when neighbor prefetch fails.
        }
    }

    private suspend fun pollCurrentOnce(channelOverride: String? = null) {
        try {
            when (val result = api.fetchCurrent(channel = channelOverride ?: _selectedChannel.value)) {
                is RadioApiResult.Success -> {
                    _currentTrack.value = result.data
                }

                is RadioApiResult.Failure -> { }
            }
        } catch (exc: CancellationException) {
            throw exc
        } catch (_: Exception) { }
    }

    private suspend fun pollArtOnce(channelOverride: String? = null) {
        val selected = normalizePersistedChannel(channelOverride ?: _selectedChannel.value)
        try {
            refreshSelectedAndAdjacentArt(
                selected = selected,
                forceSelectedRefresh = true,
            )
        } catch (exc: CancellationException) {
            throw exc
        } catch (_: Exception) { }
    }

    private suspend fun refreshSelectedAndAdjacentArt(
        selected: String,
        forceSelectedRefresh: Boolean,
    ) {
        val normalizedSelected = normalizePersistedChannel(selected)
        if (
            fetchArtForChannel(
                channel = normalizedSelected,
                forceRefresh = forceSelectedRefresh,
                isPrefetch = false,
            )
        ) {
            notifyBrowseRootChildrenChanged()
        }

        val adjacentChannels = selectedAndAdjacentChannels(normalizedSelected)
            .filter { it != normalizedSelected }

        adjacentChannels.forEach { channel ->
            if (
                fetchArtForChannel(
                    channel = channel,
                    forceRefresh = false,
                    isPrefetch = true,
                )
            ) {
                notifyBrowseRootChildrenChanged()
            }
        }
    }

    private suspend fun fetchArtForChannel(
        channel: String,
        forceRefresh: Boolean,
        isPrefetch: Boolean,
    ): Boolean {
        val normalizedChannel = normalizePersistedChannel(channel)
        val selected = normalizePersistedChannel(_selectedChannel.value)
        val cached = artByChannel[normalizedChannel]
        val nowMs = System.currentTimeMillis()
        val lastFetchAt = artFetchAtMsByChannel[normalizedChannel] ?: 0L
        val withinPrefetchCooldown = nowMs - lastFetchAt < adjacentArtPrefetchCooldownMs

        if (normalizedChannel == selected && cached != null) {
            _art.value = cached
        }

        if (isPrefetch && withinPrefetchCooldown && (cached != null || lastFetchAt > 0L)) {
            return false
        }

        if (!isPrefetch && !forceRefresh && cached != null) {
            return false
        }

        if (!artFetchInFlight.add(normalizedChannel)) {
            return false
        }

        try {
            return when (val result = api.fetchArt(channel = normalizedChannel)) {
                is RadioApiResult.Success -> {
                    val payload = result.data
                    val previous = artByChannel[normalizedChannel]
                    artByChannel[normalizedChannel] = payload
                    artFetchAtMsByChannel[normalizedChannel] = System.currentTimeMillis()

                    if (normalizedChannel == normalizePersistedChannel(_selectedChannel.value)) {
                        _art.value = payload
                    }

                    hasArtworkChanged(previous, payload) &&
                        normalizedChannel in ensureAllChannel(_channels.value)
                }

                is RadioApiResult.Failure -> {
                    artFetchAtMsByChannel[normalizedChannel] = System.currentTimeMillis()
                    false
                }
            }
        } catch (exc: CancellationException) {
            throw exc
        } catch (_: Exception) {
            artFetchAtMsByChannel[normalizedChannel] = System.currentTimeMillis()
            return false
        } finally {
            artFetchInFlight.remove(normalizedChannel)
        }
    }

    private fun refreshBrowseArtCoverage(channels: List<String>) {
        val normalizedChannels = channels
            .map(::normalizePersistedChannel)
            .distinct()
        serviceScope.launch {
            var artworkChanged = false
            normalizedChannels.forEach { channel ->
                artworkChanged = fetchArtForChannel(
                    channel = channel,
                    forceRefresh = false,
                    isPrefetch = true,
                ) || artworkChanged
            }
            if (artworkChanged) {
                notifyBrowseRootChildrenChanged()
            }
        }
    }

    private fun publishCachedArtForSelectedChannel(channel: String) {
        val normalized = normalizePersistedChannel(channel)
        val cached = artByChannel[normalized] ?: return
        _art.value = cached
    }

    private fun notifyBrowseRootChildrenChanged() {
        mediaSession?.notifyChildrenChanged(
            RADIO_BROWSE_ROOT_ID,
            ensureAllChannel(_channels.value).size,
            null,
        )
    }

    private fun selectedAndAdjacentChannels(selected: String): List<String> {
        val available = _channels.value
        if (available.isEmpty()) {
            return listOf(normalizePersistedChannel(selected))
        }

        val normalizedSelected = normalizePersistedChannel(selected)
        val selectedIndex = available.indexOf(normalizedSelected).let { if (it >= 0) it else 0 }
        val center = normalizePersistedChannel(available[selectedIndex])
        if (available.size == 1) {
            return listOf(center)
        }

        val previous = normalizePersistedChannel(
            available[floorMod(selectedIndex - 1, available.size)],
        )
        val next = normalizePersistedChannel(
            available[floorMod(selectedIndex + 1, available.size)],
        )

        return listOf(center, previous, next).distinct()
    }

    private fun pruneArtCache(validChannels: List<String>) {
        val allowedChannels = (validChannels + _selectedChannel.value)
            .map { normalizePersistedChannel(it) }
            .toSet()

        artByChannel.keys.removeAll { it !in allowedChannels }
        artFetchAtMsByChannel.keys.removeAll { it !in allowedChannels }
        artFetchInFlight.removeAll { it !in allowedChannels }
    }

    private suspend fun fadePlayerVolume(target: Float, durationMs: Long) {
        val clampedTarget = target.coerceIn(0f, 1f)
        val start = player.volume.coerceIn(0f, 1f)
        if (durationMs <= 0L || start == clampedTarget) {
            player.volume = clampedTarget
            return
        }

        val steps = 8
        val stepDuration = (durationMs / steps).coerceAtLeast(1L)
        repeat(steps) { index ->
            val progress = (index + 1) / steps.toFloat()
            player.volume = start + ((clampedTarget - start) * progress)
            delay(stepDuration)
        }

        player.volume = clampedTarget
    }

    private fun ensureAllChannel(values: List<String>): List<String> {
        val normalized = values
            .map { normalizePersistedChannel(it) }
            .filter { it.isNotBlank() && it != "all" }
            .distinct()
        return listOf("all") + normalized
    }

    private fun handlePlayerError(@Suppress("UNUSED_PARAMETER") error: PlaybackException) {
        recoveryController.onPlayerError()
    }

    private fun handlePlayerStopped() {
        clearReconnectState()
        _playbackState.value = RadioPlaybackState.Stopped
    }

    private fun handleMediaItemTransition(mediaItem: MediaItem?) {
        val channel = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        val selectedChannel = normalizePersistedChannel(_selectedChannel.value)

        syncCurrentMediaItemMetadata()

        if (selectedChannel == channel) {
            publishCachedArtForSelectedChannel(channel)
            serviceScope.launch {
                pollCurrentOnce(channelOverride = channel)
                pollArtOnce(channelOverride = channel)
            }
        }
    }

    private fun floorMod(value: Int, modulus: Int): Int {
        if (modulus <= 0) {
            return 0
        }

        val remainder = value % modulus
        return if (remainder < 0) {
            remainder + modulus
        } else {
            remainder
        }
    }

    private fun buildSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun publishSessionSnapshotAndMetadata() {
        val session = mediaSession ?: return
        val snapshot = snapshotForConnection()
        if (lastPublishedSnapshot != snapshot) {
            session.setSessionExtras(RadioSessionSnapshotCodec.toBundle(snapshot))
            lastPublishedSnapshot = snapshot
        }
        syncCurrentMediaItemMetadata()
    }

    private fun snapshotForConnection(): RadioSessionSnapshot {
        return RadioSessionSnapshot(
            playbackState = _playbackState.value,
            currentTrack = _currentTrack.value,
            art = _art.value,
            channels = _channels.value,
            selectedChannel = _selectedChannel.value,
            selectedQuality = _selectedQuality.value,
            pendingQuality = _pendingQuality.value,
        )
    }

    private inner class SessionCallback : RadioMediaSessionCallback(
        sessionExtras = {
            RadioSessionSnapshotCodec.toBundle(lastPublishedSnapshot ?: snapshotForConnection())
        },
        resolvePlaybackItems = mediaSessionStreamOpening::resolvePlaybackItems,
        selectAdjacentChannel = this@RadioPlaybackService::selectAdjacentChannel,
        setQuality = this@RadioPlaybackService::setQuality,
    ), MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(buildLibraryRootItem(), params),
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                RADIO_BROWSE_ROOT_ID -> buildLibraryRootItem()
                else -> {
                    val channel = channelFromBrowseMediaId(mediaId)
                        ?: normalizePersistedChannel(mediaId)
                    buildLibraryChannelItem(channel)
                }
            }
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = when (parentId) {
                RADIO_BROWSE_ROOT_ID -> {
                    val channels = ensureAllChannel(_channels.value)
                    refreshBrowseArtCoverage(channels)
                    channels.map(::buildLibraryChannelItem)
                }
                else -> emptyList()
            }

            return Futures.immediateFuture(
                LibraryResult.ofItemList(pagedMediaItems(items, page, pageSize), params),
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            val itemCount = buildSearchResultItems(query).size
            session.notifySearchResultChanged(browser, query, itemCount, params)
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    pagedMediaItems(buildSearchResultItems(query), page, pageSize),
                    params,
                ),
            )
        }

    }

}
