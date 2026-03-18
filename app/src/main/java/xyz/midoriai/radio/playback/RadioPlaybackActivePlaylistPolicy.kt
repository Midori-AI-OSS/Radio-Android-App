package xyz.midoriai.radio.playback

internal data class ActivePlayerPlaylistPolicy(
    val channels: List<String>,
    val startIndex: Int,
)

internal fun resolveActivePlayerPlaylistPolicy(
    selectedChannel: String,
    availableChannels: List<String>,
): ActivePlayerPlaylistPolicy {
    val normalizedSelected = normalizePersistedChannel(selectedChannel)
    val normalizedAvailableChannels = availableChannels
        .map(::normalizePersistedChannel)
        .filter { it.isNotBlank() }
        .distinct()

    val activeChannel = normalizedAvailableChannels.firstOrNull { it == normalizedSelected }
        ?: normalizedSelected

    return ActivePlayerPlaylistPolicy(
        channels = listOf(activeChannel),
        startIndex = 0,
    )
}
