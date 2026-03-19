package xyz.midoriai.radio.playback

internal data class AdjacentChannelSessionCommandRequest(
    val direction: Int,
)

internal fun createAdjacentChannelSessionCommandRequest(
    direction: Int,
): AdjacentChannelSessionCommandRequest? {
    return direction
        .takeIf { it != 0 }
        ?.let(::AdjacentChannelSessionCommandRequest)
}

internal fun parseAdjacentChannelSessionCommandRequest(
    customAction: String,
    direction: Int,
): AdjacentChannelSessionCommandRequest? {
    return if (customAction == CUSTOM_COMMAND_SELECT_ADJACENT_CHANNEL) {
        AdjacentChannelSessionCommandRequest(direction)
    } else {
        null
    }
}
