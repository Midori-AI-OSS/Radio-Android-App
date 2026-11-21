package xyz.midoriai.radio.playback

internal fun resolveAdjacentChannelSelection(
    selectedChannel: String,
    availableChannels: List<String>,
    direction: Int,
): String? {
    val normalizedAvailableChannels = availableChannels
        .map(::normalizePersistedChannel)
        .distinct()

    if (normalizedAvailableChannels.isEmpty() || direction == 0) {
        return null
    }

    val normalizedSelected = normalizePersistedChannel(selectedChannel)
    val currentIndex = normalizedAvailableChannels.indexOf(normalizedSelected)
        .let { index -> if (index >= 0) index else 0 }
    val targetIndex = positiveFloorMod(currentIndex + direction, normalizedAvailableChannels.size)
    return normalizedAvailableChannels[targetIndex]
}

/** Resolves an adjacent MediaSession selection from the newest pending target, if any. */
internal fun dispatchAdjacentChannelSelection(
    selectedChannel: String,
    pendingChannelSelection: String?,
    availableChannels: List<String>,
    direction: Int,
    selectChannel: (String) -> Unit,
): String? {
    val adjacentChannel = resolveAdjacentChannelSelection(
        selectedChannel = pendingChannelSelection ?: selectedChannel,
        availableChannels = availableChannels,
        direction = direction,
    ) ?: return null

    selectChannel(adjacentChannel)
    return adjacentChannel
}

private fun positiveFloorMod(value: Int, modulus: Int): Int {
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
