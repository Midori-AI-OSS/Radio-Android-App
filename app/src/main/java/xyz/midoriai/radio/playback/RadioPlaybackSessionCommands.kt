package xyz.midoriai.radio.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

internal const val CUSTOM_COMMAND_SET_QUALITY = "xyz.midoriai.radio.playback.SET_QUALITY"
internal const val ARG_QUALITY = "quality"

internal val SET_QUALITY_SESSION_COMMAND = SessionCommand(CUSTOM_COMMAND_SET_QUALITY, Bundle.EMPTY)
