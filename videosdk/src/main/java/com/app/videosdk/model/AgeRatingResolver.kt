package com.app.videosdk.model

/** Resolves the backend-provided classification without mapping or rewriting it. */
object AgeRatingResolver {

    /**
     * Returns the exact value to render, or `null` when neither supported API field
     * contains visible text. Whitespace is inspected but never trimmed from the result.
     */
    @JvmStatic
    fun resolve(video: PlayerModel?): String? {
        val ageRating = video?.ageRating
        if (!ageRating.isNullOrBlank()) return ageRating

        return video?.contentRating?.takeUnless(String::isBlank)
    }
}
