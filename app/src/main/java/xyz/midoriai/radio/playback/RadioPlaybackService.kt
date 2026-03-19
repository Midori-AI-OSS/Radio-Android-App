package xyz.midoriai.radio.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
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
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
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
    private val reconnectDelaysMs: List<Long> = listOf(50L, 100L, 200L, 400L, 800L, 1500L)
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
                exo.addListener(PlayerEventListener())
            }
    }

    private val sessionPlayer: Player by lazy {
        object : ForwardingPlayer(player) {
            override fun play() {
                this@RadioPlaybackService.play()
            }

            override fun pause() {
                this@RadioPlaybackService.pausePlayback()
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) {
                    this@RadioPlaybackService.play()
                } else {
                    this@RadioPlaybackService.pausePlayback()
                }
            }

            override fun stop() {
                this@RadioPlaybackService.pausePlayback()
            }

            override fun seekToNextMediaItem() {
                this@RadioPlaybackService.selectAdjacentChannel(1)
            }

            override fun seekToPreviousMediaItem() {
                this@RadioPlaybackService.selectAdjacentChannel(-1)
            }

            override fun seekToNext() {
                this@RadioPlaybackService.selectAdjacentChannel(1)
            }

            override fun seekToPrevious() {
                this@RadioPlaybackService.selectAdjacentChannel(-1)
            }
        }
    }

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
    private var reconnectAttempt: Int = 0
    private var reconnectJob: Job? = null
    private var channelSwitchJob: Job? = null
    private var metadataPollJob: Job? = null
    private var channelPollJob: Job? = null
    private var connectGeneration: Long = 0
    private var lastObservedChannel: String? = null
    private var lastObservedQuality: String? = null
    private var lastKnownPlayerChannel: String? = null
    private var lastPlaylistChannels: List<String> = emptyList()
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
                        reconnectAttempt = 0
                        serviceScope.launch {
                            pollCurrentOnce(channelOverride = normalized)
                            pollArtOnce(channelOverride = normalized)
                        }
                    } else {
                        switchChannelWithFade(normalized)
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
                player.playWhenReady = true
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
            }
            return
        }

        playbackDesired = true
        reconnectAttempt = 0
        player.volume = playerVolumeNormal
        connectToSelectedStream(RadioPlaybackState.Connecting)
    }

    private fun pausePlayback() {
        playbackDesired = false
        clearReconnectState()
        channelSwitchJob?.cancel()
        channelSwitchJob = null

        player.playWhenReady = false
        player.pause()
        player.volume = playerVolumeNormal
        _playbackState.value = RadioPlaybackState.Stopped
    }

    private fun selectAdjacentChannel(direction: Int) {
        val adjacentChannel = resolveAdjacentChannelSelection(
            selectedChannel = _selectedChannel.value,
            availableChannels = _channels.value,
            direction = direction,
        ) ?: return

        setChannel(adjacentChannel)
    }

    private fun setQuality(quality: String) {
        serviceScope.launch {
            settingsRepository.setQuality(quality)
        }
    }

    private fun setChannel(channel: String) {
        serviceScope.launch {
            settingsRepository.setChannel(channel)
        }
    }

    private fun selectChannelImmediately(channel: String) {
        val normalizedChannel = normalizePersistedChannel(channel)
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
        channelSwitchJob?.cancel()
        channelSwitchJob = serviceScope.launch {
            if (!playbackDesired) {
                return@launch
            }

            reconnectAttempt = 0
            _playbackState.value = RadioPlaybackState.SwitchingChannel(toChannelLabel(channel))

            fadePlayerVolume(0f, channelSwitchFadeDurationMs)
            connectToSelectedStream(
                defaultState = RadioPlaybackState.Connecting,
                channelOverride = channel,
            )
            fadePlayerVolume(playerVolumeNormal, channelSwitchFadeDurationMs)

            pollCurrentOnce(channelOverride = channel)
            pollArtOnce(channelOverride = channel)
        }
    }

    private fun connectToSelectedStream(
        defaultState: RadioPlaybackState,
        channelOverride: String? = null,
    ) {
        cancelPendingReconnectJob()
        connectGeneration += 1

        val channel = normalizePersistedChannel(channelOverride ?: _selectedChannel.value)
        val quality = resolveQualityForConnect()
        val streamUrl = buildStreamUrl(
            baseUrl = RadioApiDefaults.DEFAULT_BASE_URL,
            channel = channel,
            quality = quality,
        )

        if (isDebugBuild) {
            Log.d(
                "MidoriAIRadio",
                "Radio endpoints: stream=$streamUrl current=/radio/v1/current art=/radio/v1/art artImage=/radio/v1/art/image health=/health reconnectDelaysMs=$reconnectDelaysMs",
            )
        }

        _playbackState.value = defaultState

        player.volume = playerVolumeNormal
        syncPlayerPlaylist(channel, forceRebuild = lastPlaylistQuality != quality)
        val targetIndex = lastPlaylistChannels.indexOf(channel).let { if (it >= 0) it else 0 }
        if (
            shouldSeekToReconnectTarget(
                currentMediaItemIndex = player.currentMediaItemIndex,
                targetIndex = targetIndex,
                playbackState = player.playbackState,
            )
        ) {
            player.seekToDefaultPosition(targetIndex)
        }
        player.prepare()
        player.playWhenReady = true
    }

    private fun cancelPendingReconnectJob() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun clearReconnectState() {
        cancelPendingReconnectJob()
        reconnectAttempt = 0
    }

    private fun isPlaybackHealthyForReconnect(): Boolean {
        return isHealthyPlayback(
            playbackState = player.playbackState,
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
        )
    }

    private fun markPlaybackRecovered() {
        clearReconnectState()
        _playbackState.value = RadioPlaybackState.Playing
        player.volume = playerVolumeNormal
    }

    private fun queueReconnect(@Suppress("UNUSED_PARAMETER") reason: String) {
        if (!playbackDesired) {
            return
        }

        if (isPlaybackHealthyForReconnect()) {
            markPlaybackRecovered()
            return
        }

        cancelPendingReconnectJob()

        reconnectAttempt += 1
        val delayIndex = (reconnectAttempt - 1).coerceAtMost(reconnectDelaysMs.lastIndex)
        val delayMs = reconnectDelaysMs[delayIndex]
        val capturedGeneration = connectGeneration

        _playbackState.value = RadioPlaybackState.Reconnecting(reconnectAttempt, delayMs)

        reconnectJob = serviceScope.launch {
            delay(delayMs)
            if (
                !shouldExecuteDelayedReconnect(
                    playbackDesired = playbackDesired,
                    queuedGeneration = capturedGeneration,
                    currentGeneration = connectGeneration,
                    playbackState = player.playbackState,
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                )
            ) {
                return@launch
            }

            connectToSelectedStream(RadioPlaybackState.Connecting)
        }
    }

    private fun resolveQualityForConnect(): String {
        val queuedQuality = _pendingQuality.value
        if (!queuedQuality.isNullOrBlank()) {
            val normalized = normalizeQuality(queuedQuality)
            _activeQuality.value = normalized
            _pendingQuality.value = null
            return normalized
        }

        val active = normalizeQuality(_activeQuality.value)
        if (active != _activeQuality.value) {
            _activeQuality.value = active
        }

        return active
    }

    private fun syncPlayerPlaylist(
        selectedChannel: String,
        forceRebuild: Boolean = false,
    ) {
        val normalizedSelected = normalizePersistedChannel(selectedChannel)
        val resolvedChannels = ensureAllChannel(_channels.value + normalizedSelected)
        if (_channels.value != resolvedChannels) {
            _channels.value = resolvedChannels
        }

        val activeQuality = normalizeQuality(_activeQuality.value)
        val activePlaylist = resolveActivePlayerPlaylistPolicy(
            selectedChannel = normalizedSelected,
            availableChannels = resolvedChannels,
        )
        val needsRebuild = forceRebuild ||
            lastPlaylistChannels != activePlaylist.channels ||
            lastPlaylistQuality != activeQuality ||
            player.mediaItemCount != activePlaylist.channels.size

        if (needsRebuild) {
            val mediaItems = activePlaylist.channels
                .map { channel -> buildMediaItem(channel = channel, quality = activeQuality) }
            player.setMediaItems(mediaItems, activePlaylist.startIndex, C.TIME_UNSET)
            lastPlaylistChannels = activePlaylist.channels
            lastPlaylistQuality = activeQuality
            lastKnownPlayerChannel = activePlaylist.channels
                .getOrNull(activePlaylist.startIndex)
                ?: normalizedSelected
            lastPublishedMetadataFingerprint = null

            if (playbackDesired) {
                player.prepare()
                player.playWhenReady = true
            } else {
                player.playWhenReady = false
            }
        } else if (player.currentMediaItemIndex != activePlaylist.startIndex) {
            player.seekToDefaultPosition(activePlaylist.startIndex)
        }

        syncCurrentMediaItemMetadata()
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
        return resolveActivePlayerPlaylistPolicy(
            selectedChannel = selectedChannel,
            availableChannels = _channels.value,
        ).channels
            .map { channel -> buildMediaItem(channel = channel, quality = quality) }
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
        val currentChannel = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
            ?: normalizePersistedChannel(_selectedChannel.value)
        val currentIndex = if (player.currentMediaItemIndex >= 0) {
            player.currentMediaItemIndex
        } else {
            lastPlaylistChannels.indexOf(currentChannel)
        }
        if (currentIndex !in 0 until player.mediaItemCount) {
            return
        }

        val fingerprint = buildMetadataFingerprint(currentChannel)
        if (fingerprint == lastPublishedMetadataFingerprint) {
            return
        }

        val updatedItem = buildMediaItem(
            channel = currentChannel,
            quality = lastPlaylistQuality,
        )
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
        fetchArtForChannel(
            channel = normalizedSelected,
            forceRefresh = forceSelectedRefresh,
            isPrefetch = false,
        )

        val adjacentChannels = selectedAndAdjacentChannels(normalizedSelected)
            .filter { it != normalizedSelected }

        adjacentChannels.forEach { channel ->
            fetchArtForChannel(
                channel = channel,
                forceRefresh = false,
                isPrefetch = true,
            )
        }
    }

    private suspend fun fetchArtForChannel(
        channel: String,
        forceRefresh: Boolean,
        isPrefetch: Boolean,
    ) {
        val normalizedChannel = normalizePersistedChannel(channel)
        val selected = normalizePersistedChannel(_selectedChannel.value)
        val cached = artByChannel[normalizedChannel]
        val nowMs = System.currentTimeMillis()
        val lastFetchAt = artFetchAtMsByChannel[normalizedChannel] ?: 0L
        val withinPrefetchCooldown = nowMs - lastFetchAt < adjacentArtPrefetchCooldownMs

        if (normalizedChannel == selected && cached != null) {
            _art.value = cached
        }

        if (isPrefetch && cached != null && withinPrefetchCooldown) {
            return
        }

        if (!isPrefetch && !forceRefresh && cached != null) {
            return
        }

        if (!artFetchInFlight.add(normalizedChannel)) {
            return
        }

        try {
            when (val result = api.fetchArt(channel = normalizedChannel)) {
                is RadioApiResult.Success -> {
                    val payload = result.data
                    artByChannel[normalizedChannel] = payload
                    artFetchAtMsByChannel[normalizedChannel] = System.currentTimeMillis()

                    if (normalizedChannel == normalizePersistedChannel(_selectedChannel.value)) {
                        _art.value = payload
                    }
                }

                is RadioApiResult.Failure -> { }
            }
        } catch (exc: CancellationException) {
            throw exc
        } catch (_: Exception) { } finally {
            artFetchInFlight.remove(normalizedChannel)
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

    private fun handlePlayerError(error: PlaybackException) {
        val message = error.message?.trim().orEmpty()
        queueReconnect(if (message.isNotEmpty()) message else "Playback error")
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

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: ControllerInfo,
        ): ConnectionResult {
            val sessionCommands = ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SELECT_ADJACENT_CHANNEL_SESSION_COMMAND)
                .add(SET_QUALITY_SESSION_COMMAND)
                .build()

            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .setSessionExtras(RadioSessionSnapshotCodec.toBundle(lastPublishedSnapshot ?: snapshotForConnection()))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            parseAdjacentChannelSessionCommandRequest(
                customAction = customCommand.customAction,
                direction = args.getInt(ARG_DIRECTION),
            )?.let { request ->
                selectAdjacentChannel(request.direction)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            return when (customCommand.customAction) {
                CUSTOM_COMMAND_SET_QUALITY -> {
                    setQuality(args.getString(ARG_QUALITY).orEmpty())
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }

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
                RADIO_BROWSE_ROOT_ID -> ensureAllChannel(_channels.value)
                    .map(::buildLibraryChannelItem)
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

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val requestedChannel = resolveRequestedChannel(mediaItems)
            val normalizedChannel = normalizePersistedChannel(requestedChannel)
            val quality = resolveQualityForConnect()
            val playbackItems = buildPlaybackItemsForChannel(
                selectedChannel = normalizedChannel,
                quality = quality,
            )
            val targetIndex = playbackItems.indexOfFirst { it.mediaId == normalizedChannel }
                .let { if (it >= 0) it else 0 }

            selectChannelImmediately(normalizedChannel)

            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    playbackItems,
                    targetIndex,
                    C.TIME_UNSET,
                ),
            )
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!playbackDesired) {
                return
            }

            if (isPlaying) {
                markPlaybackRecovered()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!playbackDesired) {
                return
            }

            if (
                isHealthyPlayback(
                    playbackState = playbackState,
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                )
            ) {
                markPlaybackRecovered()
            }

            if (playbackState == Player.STATE_ENDED) {
                queueReconnect("Stream ended")
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playbackDesired && !playWhenReady) {
                _playbackState.value = RadioPlaybackState.Stopped
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                syncCurrentMediaItemMetadata()
            }
            val channel = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
            val selectedChannel = normalizePersistedChannel(_selectedChannel.value)

            if (
                shouldRestoreSelectedChannelAfterTransition(
                    playbackDesired = playbackDesired,
                    selectedChannel = selectedChannel,
                    transitionedChannel = channel,
                    reason = reason,
                )
            ) {
                reconnectAttempt = 0
                connectToSelectedStream(RadioPlaybackState.Connecting)
                return
            }

            if (channel == lastKnownPlayerChannel) {
                return
            }

            lastKnownPlayerChannel = channel

            if (playbackDesired) {
                reconnectAttempt = 0
                _playbackState.value = RadioPlaybackState.SwitchingChannel(toChannelLabel(channel))
            }

            syncCurrentMediaItemMetadata()

            if (selectedChannel != channel) {
                setChannel(channel)
            } else {
                publishCachedArtForSelectedChannel(channel)
                serviceScope.launch {
                    pollCurrentOnce(channelOverride = channel)
                    pollArtOnce(channelOverride = channel)
                }
            }
        }
    }
}
