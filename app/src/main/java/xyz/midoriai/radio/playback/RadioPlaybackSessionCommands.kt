package xyz.midoriai.radio.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

internal const val CUSTOM_COMMAND_SET_QUALITY = "xyz.midoriai.radio.playback.SET_QUALITY"
internal const val CUSTOM_COMMAND_SELECT_ADJACENT_CHANNEL = "xyz.midoriai.radio.playback.SELECT_ADJACENT_CHANNEL"
internal const val ARG_DIRECTION = "direction"
internal const val ARG_QUALITY = "quality"

internal val SELECT_ADJACENT_CHANNEL_SESSION_COMMAND = SessionCommand(
    CUSTOM_COMMAND_SELECT_ADJACENT_CHANNEL,
    Bundle.EMPTY,
)
internal val SET_QUALITY_SESSION_COMMAND = SessionCommand(CUSTOM_COMMAND_SET_QUALITY, Bundle.EMPTY)
