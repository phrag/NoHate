package com.nohate.app.platform

/**
 * Rich comment record returned by [PostImporter] and [CommentProvider]. Carries
 * the optional moderation metadata that [com.nohate.app.data.FlaggedItem] now
 * stores. All fields except [text] are nullable — when the source can't supply
 * them (HTML scrape fallback, unauthenticated GraphQL), the moderation UI
 * gracefully hides the corresponding buttons.
 */
data class CommentData(
    val text: String,
    val commentId: String? = null,
    val authorId: String? = null,
    val authorHandle: String? = null,
    /** IG user ID of the post owner — compare to SecureStore.getIgUserId() for `ownedByMe`. */
    val postOwnerId: String? = null,
)
