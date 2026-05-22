package com.nohate.app.data

data class FlaggedItem(
    val text: String,
    val sourceUrl: String? = null,
    val commentId: String? = null,
    val authorId: String? = null,
    val authorHandle: String? = null,
    val ownedByMe: Boolean = false,
)


