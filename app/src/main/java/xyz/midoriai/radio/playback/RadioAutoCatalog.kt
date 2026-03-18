package xyz.midoriai.radio.playback

import java.util.Locale

internal const val RADIO_BROWSE_ROOT_ID = "midori:root"
private const val RADIO_BROWSE_CHANNEL_PREFIX = "midori:channel:"

private val SEARCH_SPACING_REGEX = Regex("\\s+")
private val SEARCH_PUNCTUATION_REGEX = Regex("[^\\p{L}\\p{N}]+")
private val SEARCH_STOP_WORDS = setOf(
    "ai",
    "channel",
    "from",
    "listen",
    "media",
    "midori",
    "midoriai",
    "music",
    "play",
    "radio",
    "search",
    "station",
    "stream",
    "the",
)

internal fun toBrowseChannelMediaId(channel: String): String {
    return "$RADIO_BROWSE_CHANNEL_PREFIX${normalizePersistedChannel(channel)}"
}

internal fun channelFromBrowseMediaId(mediaId: String): String? {
    val normalizedId = mediaId.trim()
    if (!normalizedId.startsWith(RADIO_BROWSE_CHANNEL_PREFIX)) {
        return null
    }

    return normalizePersistedChannel(normalizedId.removePrefix(RADIO_BROWSE_CHANNEL_PREFIX))
}

internal fun normalizeSearchText(value: String?): String {
    val normalized = value
        ?.trim()
        ?.lowercase(Locale.US)
        ?.replace(SEARCH_PUNCTUATION_REGEX, " ")
        ?.trim()
        .orEmpty()

    return normalized.replace(SEARCH_SPACING_REGEX, " ")
}

internal fun resolveChannelForSearch(
    query: String?,
    availableChannels: List<String>,
): String {
    val resolvedChannels = ensureSearchableChannels(availableChannels)
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank()) {
        return "all"
    }

    val meaningfulQuery = stripSearchStopWords(normalizedQuery)
    val queryTokens = tokenizeSearchValue(meaningfulQuery.ifBlank { normalizedQuery })
    val compactQuery = compactSearchText(meaningfulQuery.ifBlank { normalizedQuery })

    val bestMatch = resolvedChannels
        .asSequence()
        .filter { it != "all" }
        .map { channel -> channel to scoreChannelMatch(channel, normalizedQuery, queryTokens, compactQuery) }
        .filter { (_, score) -> score >= SEARCH_MATCH_THRESHOLD }
        .maxWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenByDescending { it.first.length })

    return bestMatch?.first ?: "all"
}

private fun ensureSearchableChannels(availableChannels: List<String>): List<String> {
    return (listOf("all") + availableChannels)
        .map(::normalizePersistedChannel)
        .filter { it.isNotBlank() }
        .distinct()
}

private fun stripSearchStopWords(value: String): String {
    return tokenizeSearchValue(value)
        .filterNot { it in SEARCH_STOP_WORDS }
        .joinToString(" ")
}

private fun tokenizeSearchValue(value: String): List<String> {
    return normalizeSearchText(value)
        .split(' ')
        .filter { it.isNotBlank() }
}

private fun compactSearchText(value: String): String {
    return normalizeSearchText(value).replace(" ", "")
}

private fun scoreChannelMatch(
    channel: String,
    normalizedQuery: String,
    queryTokens: List<String>,
    compactQuery: String,
): Int {
    val normalizedChannel = normalizePersistedChannel(channel)
    val displayChannel = normalizeSearchText(toChannelDisplayName(normalizedChannel))
    val compactChannel = compactSearchText(normalizedChannel)
    val compactDisplayChannel = compactSearchText(displayChannel)
    val channelTokens = tokenizeSearchValue(displayChannel.ifBlank { normalizedChannel })

    if (normalizedQuery == displayChannel || normalizedQuery == normalizedChannel) {
        return 1_000
    }

    if (compactQuery.isNotBlank() && (compactQuery == compactChannel || compactQuery == compactDisplayChannel)) {
        return 950
    }

    if (
        normalizedQuery.contains(displayChannel) ||
        normalizedQuery.contains(normalizedChannel) ||
        (compactQuery.isNotBlank() &&
            (compactQuery.contains(compactChannel) || compactQuery.contains(compactDisplayChannel)))
    ) {
        return 800 + normalizedChannel.length
    }

    val overlappingTokens = queryTokens.count { token ->
        token in channelTokens ||
            compactChannel.contains(token) ||
            compactDisplayChannel.contains(token) ||
            token.contains(compactChannel) ||
            token.contains(compactDisplayChannel)
    }

    if (overlappingTokens == 0) {
        return 0
    }

    val allChannelTokensMatched = channelTokens.isNotEmpty() && channelTokens.all { it in queryTokens }
    return (overlappingTokens * 100) + if (allChannelTokensMatched) 50 else 0
}

private const val SEARCH_MATCH_THRESHOLD = 100
