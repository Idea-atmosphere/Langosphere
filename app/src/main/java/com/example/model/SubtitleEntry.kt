package com.example.model

data class SubtitleEntry(
    val start: Double,
    val end: Double,
    val text: String,
    val language: String = ""
)
