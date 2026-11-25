package com.arcturus.haven.launcher.core.model

data class NewsFeedSource(
    val title: String,
    val url: String,
    val category: String,
    val description: String = "",
    val accentColor: Int = 0xFF5267C9.toInt(),
) {
    val id: String get() = url.trim().lowercase()
}

data class NewsFeedArticle(
    val sourceId: String,
    val sourceTitle: String,
    val title: String,
    val link: String,
    val publishedAtMillis: Long,
    val description: String = "",
) {
    val id: String get() = "$sourceId|$link"
}
