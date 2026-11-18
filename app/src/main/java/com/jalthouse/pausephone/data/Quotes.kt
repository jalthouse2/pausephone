package com.jalthouse.pausephone.data

data class Quote(val text: String, val by: String)

/** Shown one at a time under the breathing cloud, changing every two breaths. */
val QUOTES = listOf(
    Quote("Breathing in, I calm body and mind. Breathing out, I smile.", "Thich Nhat Hanh"),
    Quote("Nothing here needs you right now.", "[pause]"),
    Quote("Very little is needed to make a happy life.", "Marcus Aurelius"),
    Quote("Life is long if you know how to use it.", "Seneca"),
    Quote("Take your time. Everything will still be here.", "[pause]"),
    Quote("Attention is the rarest and purest form of generosity.", "Simone Weil"),
    Quote("This breath is the only one happening.", "[pause]"),
)
