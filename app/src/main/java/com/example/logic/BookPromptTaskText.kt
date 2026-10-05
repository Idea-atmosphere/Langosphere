package com.example.logic

import com.example.logic.BookPromptTemplates.BookPromptMode
import com.example.logic.BookPromptTemplates.CEFR_PLACEHOLDER
import com.example.logic.BookPromptTemplates.MAX_WORDS_PER_SENTENCE
import com.example.logic.BookPromptTemplates.MAX_WORDS_VOCAB_MODE

/**
 * The "## Task" paragraph of a book prompt: one short statement of what the
 * model is being asked to do for the selected [BookPromptMode].
 *
 * It lives beside [BookPromptTemplates] rather than inside it only to keep
 * that object focused on the contract (anchors, schema, checklist). It is a
 * package-level function, so `BookPromptTemplates` calls it unqualified.
 *
 * @param mode the selected learning goal.
 * @param source the book's language, or a phrase such as "the book's own
 *   language" when detection is left to the model.
 * @param target the language explanations and translations are written in.
 */
internal fun taskFor(
    mode: BookPromptMode,
    source: String,
    target: String,
): String = when (mode) {
    BookPromptMode.TRANSLATION_ONLY ->
        "Translate every sentence line I send from $source into $target, one entry per line, and return them as the JSON package described below. " +
            "Nothing else: no lesson, no word list, no commentary. Accuracy and full coverage matter more than elegance - the app prints your translation directly under the matching line of the page."

    BookPromptMode.READING_LEARNING ->
        "For every sentence line I send, produce a complete mini lesson at $CEFR_PLACEHOLDER: a natural $target translation, the grammar and structure worth learning, IPA where it helps, and up to $MAX_WORDS_PER_SENTENCE key words with their own examples. " +
            "The app opens the `lesson` object when I tap a sentence, so every entry needs one - a translation without a lesson leaves that button half empty."

    BookPromptMode.VOCAB_MINING ->
        "For every sentence line I send, give the $target translation and mine at most $MAX_WORDS_VOCAB_MODE genuinely useful words or collocations from that same line, each with its meaning in this context, two short examples and IPA. " +
            "Every line still gets its own entry even when it yields no word, because the app has to keep the page and the package in step."

    BookPromptMode.SIMPLIFY ->
        "Rewrite every sentence line I send in $source at $CEFR_PLACEHOLDER, keep the original beside it, and translate the simplified version into $target. " +
            "The result is a graded reader: same story, same order, same sentence count, easier language."

    BookPromptMode.GRAMMAR_COACH ->
        "Act as my grammar coach for the passage I send: take its hardest sentences apart, name the structures, and teach me the patterns this author keeps reusing so the next pages get easier. " +
            "Answer in the chat - this mode is not imported as a file."

    BookPromptMode.COMPREHENSION ->
        "Help me check that I understood the passage I send: summarise it, lay out the characters and the tone, surface what is implied rather than said, and finish with a few comprehension questions at $CEFR_PLACEHOLDER. " +
            "Answer in the chat - this mode is not imported as a file."

    BookPromptMode.WORD_ANALYSIS ->
        "I will send one word or one sentence that blocked me mid-page, together with the sentence it came from. Explain it deeply in $target: meaning in this exact context, pronunciation, form, register, examples and the look-alikes I might confuse it with. " +
            "Answer in the chat - this mode is not imported as a file."
}
