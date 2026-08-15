package xyz.midoriai.radio.playback

import android.os.Bundle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.midoriai.radio.radioapi.ArtPayload
import xyz.midoriai.radio.radioapi.CurrentPayload
import xyz.midoriai.radio.ui.screens.nowplaying.RadioPlaybackState

internal data class RadioSessionSnapshot(
    val playbackState: RadioPlaybackState = RadioPlaybackState.Idle,
    val currentTrack: CurrentPayload? = null,
    val art: ArtPayload? = null,
    val channels: List<String> = listOf("all"),
    val selectedChannel: String = "all",
    val selectedQuality: String = "medium",
    val pendingQuality: String? = null,
)

internal object RadioSessionSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private const val KEY_PLAYBACK_STATE_TYPE = "radio.playback_state_type"
    private const val KEY_PLAYBACK_SWITCHING_CHANNEL = "radio.playback_switching_channel"
    private const val KEY_PLAYBACK_RECONNECT_ATTEMPT = "radio.playback_reconnect_attempt"
    private const val KEY_PLAYBACK_RECONNECT_DELAY_MS = "radio.playback_reconnect_delay_ms"
    private const val KEY_CURRENT_TRACK_JSON = "radio.current_track_json"
    private const val KEY_ART_JSON = "radio.art_json"
    private const val KEY_CHANNELS = "radio.channels"
    private const val KEY_SELECTED_CHANNEL = "radio.selected_channel"
    private const val KEY_SELECTED_QUALITY = "radio.selected_quality"
    private const val KEY_PENDING_QUALITY = "radio.pending_quality"

    fun toBundle(snapshot: RadioSessionSnapshot): Bundle {
        val playbackState = snapshot.playbackState
        val playbackType = when (playbackState) {
            RadioPlaybackState.Idle -> "idle"
            RadioPlaybackState.Connecting -> "connecting"
            is RadioPlaybackState.SwitchingChannel -> "switching"
            RadioPlaybackState.Playing -> "playing"
            is RadioPlaybackState.Reconnecting -> "reconnecting"
            RadioPlaybackState.Stopped -> "stopped"
        }

        return Bundle().apply {
            putString(KEY_PLAYBACK_STATE_TYPE, playbackType)
            putString(
                KEY_PLAYBACK_SWITCHING_CHANNEL,
                (playbackState as? RadioPlaybackState.SwitchingChannel)?.channel,
            )
            putInt(
                KEY_PLAYBACK_RECONNECT_ATTEMPT,
                (playbackState as? RadioPlaybackState.Reconnecting)?.attempt ?: 0,
            )
            putLong(
                KEY_PLAYBACK_RECONNECT_DELAY_MS,
                (playbackState as? RadioPlaybackState.Reconnecting)?.nextDelayMs ?: 0L,
            )
            putString(
                KEY_CURRENT_TRACK_JSON,
                snapshot.currentTrack?.let { json.encodeToString(CurrentPayload.serializer(), it) },
            )
            putString(
                KEY_ART_JSON,
                snapshot.art?.let { json.encodeToString(ArtPayload.serializer(), it) },
            )
            putStringArrayList(KEY_CHANNELS, ArrayList(snapshot.channels))
            putString(KEY_SELECTED_CHANNEL, snapshot.selectedChannel)
            putString(KEY_SELECTED_QUALITY, snapshot.selectedQuality)
            putString(KEY_PENDING_QUALITY, snapshot.pendingQuality)
        }
    }

    fun fromBundle(bundle: Bundle?): RadioSessionSnapshot? {
        if (bundle == null || bundle.isEmpty) {
            return null
        }

        val playbackState = when (bundle.getString(KEY_PLAYBACK_STATE_TYPE)) {
            "connecting" -> RadioPlaybackState.Connecting
            "switching" -> RadioPlaybackState.SwitchingChannel(
                bundle.getString(KEY_PLAYBACK_SWITCHING_CHANNEL).orEmpty(),
            )
            "playing" -> RadioPlaybackState.Playing
            "reconnecting" -> RadioPlaybackState.Reconnecting(
                attempt = bundle.getInt(KEY_PLAYBACK_RECONNECT_ATTEMPT),
                nextDelayMs = bundle.getLong(KEY_PLAYBACK_RECONNECT_DELAY_MS),
            )
            "stopped" -> RadioPlaybackState.Stopped
            else -> RadioPlaybackState.Idle
        }

        val channels = bundle.getStringArrayList(KEY_CHANNELS)
            ?.map { normalizePersistedChannel(it) }
            ?.distinct()
            ?.ifEmpty { listOf("all") }
            ?: listOf("all")

        return RadioSessionSnapshot(
            playbackState = playbackState,
            currentTrack = bundle.getString(KEY_CURRENT_TRACK_JSON)
                ?.let { raw ->
                    runCatching { json.decodeFromString(CurrentPayload.serializer(), raw) }.getOrNull()
                },
            art = bundle.getString(KEY_ART_JSON)
                ?.let { raw ->
                    runCatching { json.decodeFromString(ArtPayload.serializer(), raw) }.getOrNull()
                },
            channels = channels,
            selectedChannel = normalizePersistedChannel(
                bundle.getString(KEY_SELECTED_CHANNEL).orEmpty(),
            ),
            selectedQuality = bundle.getString(KEY_SELECTED_QUALITY)?.ifBlank { "medium" } ?: "medium",
            pendingQuality = bundle.getString(KEY_PENDING_QUALITY)?.ifBlank { null },
        )
    }
}
