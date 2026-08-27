package com.example.model

/**
 * A single flashcard in the app's built-in Leitner box (spaced repetition).
 *
 * [word] and [definition] are filled in from the offline dictionary when the
 * user taps "افزودن به جعبه لایتنر" ("Add to Leitner box") in the dictionary
 * bottom sheet — see AppViewModel.addActiveWordToLeitner().
 *
 * [boxLevel] is 1..5 (Leitner's classic 5-box system): higher boxes are
 * reviewed less often. [nextReviewAt] is a millisecond epoch timestamp; the
 * card is "due" once the current time passes it. See LeitnerBoxManager for
 * the exact interval per box and how cards move between boxes.
 */
data class LeitnerCard(
    val id: Long,
    val word: String,
    val definition: String,
    val boxLevel: Int,
    val nextReviewAt: Long,
    val createdAt: Long
)
