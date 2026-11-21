package xyz.midoriai.radio.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Service-facing MediaSession admission policy shared by the library callback and its tests. */
internal open class RadioMediaSessionCallback(
    private val sessionExtras: () -> Bundle,
    private val resolvePlaybackItems: (List<MediaItem>) -> List<MediaItem>,
    private val selectAdjacentChannel: (Int) -> Unit,
    private val setQuality: (String) -> Unit,
) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: ControllerInfo,
    ): ConnectionResult {
        val sessionCommands = ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SELECT_ADJACENT_CHANNEL_SESSION_COMMAND)
            .add(SET_QUALITY_SESSION_COMMAND)
            .build()

        return ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(RADIO_SESSION_PLAYER_COMMANDS)
            .setSessionExtras(sessionExtras())
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

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(
                resolvePlaybackItems(mediaItems),
                0,
                C.TIME_UNSET,
            ),
        )
    }
}
