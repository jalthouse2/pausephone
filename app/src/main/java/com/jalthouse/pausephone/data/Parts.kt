package com.jalthouse.pausephone.data

/**
 * A part of an app you can pause on its own, like Instagram Reels or YouTube Shorts.
 * [pause] recognises it by the screens that only that part shows (their view ids).
 * Apps change these now and then, so this is experimental: if a part stops being
 * recognised, its ids here need updating.
 */
data class AppPart(val id: String, val label: String, val pkg: String, val viewIds: List<String>)

val APP_PARTS = listOf(
    AppPart(
        id = "part:instagram.reels",
        label = "Instagram Reels",
        pkg = "com.instagram.android",
        viewIds = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/clips_video_container",
        ),
    ),
    AppPart(
        id = "part:youtube.shorts",
        label = "YouTube Shorts",
        pkg = "com.google.android.youtube",
        viewIds = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
        ),
    ),
)

val PART_PACKAGES: Set<String> = APP_PARTS.map { it.pkg }.toSet()

fun partFor(id: String): AppPart? = APP_PARTS.find { it.id == id }
