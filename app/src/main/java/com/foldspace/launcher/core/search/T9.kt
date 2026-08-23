package com.foldspace.launcher.core.search

/**
 * Numeric-keypad search, the way a feature phone did it: 2 is abc, 3 is def,
 * and typing 4663 finds Gmail.
 *
 * Worth having because it is far faster than it sounds — three or four digits
 * usually leave one candidate — and because it survives typing while walking,
 * which a full keyboard does not.
 *
 * **It only works on the Latin alphabet.** That is not an implementation
 * shortcut, it is what T9 is: a mapping from twenty-six letters onto eight
 * keys. 注音 has thirty-seven symbols and no keypad convention on Android, and
 * a 漢字 label has no letters at all — so a Chinese label produces an empty
 * signature and is never matched by a numeric query. Those labels keep the
 * substring search they already had, and the UI says so rather than leaving an
 * empty result to be interpreted.
 */
object T9 {

    /** Ranks, best first. [NO_MATCH] is not a rank; it means "not a result". */
    const val FULL_PREFIX = 0
    const val WORD_PREFIX = 1
    const val CONTAINS = 2
    const val NO_MATCH = -1

    /** Shortest query worth running. One digit matches roughly everything. */
    const val MIN_QUERY_LENGTH = 2

    /**
     * The keypad. Index is the digit, so `KEYS[7]` is the letters on 7 —
     * which is `pqrs`, four of them, as on every phone that ever shipped this.
     */
    private val KEYS = arrayOf(
        "", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz",
    )

    private val digitOf: Map<Char, Char> = buildMap {
        KEYS.forEachIndexed { digit, letters ->
            letters.forEach { letter -> put(letter, '0' + digit) }
        }
    }

    /** True when a query is worth handing to [score] at all. */
    fun isNumericQuery(query: String): Boolean =
        query.length >= MIN_QUERY_LENGTH && query.all { it in '0'..'9' }

    /**
     * A label's digit signature.
     *
     * Anything that is not a Latin letter or an ASCII digit is dropped rather
     * than mapped to a separator: a user typing a name does not type its
     * spaces or its punctuation, so "Google Maps" has to be reachable as one
     * run of digits.
     */
    fun signature(text: String): String {
        val out = StringBuilder(text.length)
        for (raw in text) {
            val c = raw.lowercaseChar()
            when {
                c in '0'..'9' -> out.append(c)
                else -> digitOf[c]?.let(out::append)
            }
        }
        return out.toString()
    }

    /**
     * Splits on anything that is not a letter or a digit, so each word can be
     * matched from its own start. Typing 6277 should find "Google Maps"; on
     * the whole-label signature alone it would not, because Maps is not where
     * the label begins.
     */
    fun words(text: String): List<String> =
        text.split(*SEPARATORS).filter { it.isNotBlank() }

    /** A label reduced once, so a keystroke does not re-derive it per app. */
    fun index(label: String): T9Index =
        T9Index(full = signature(label), words = words(label).map(::signature).filter { it.isNotEmpty() })

    /** [NO_MATCH], or a rank where lower is better. */
    fun score(index: T9Index, digits: String): Int {
        if (index.full.isEmpty() || digits.isEmpty()) return NO_MATCH
        if (index.full.startsWith(digits)) return FULL_PREFIX
        if (index.words.any { it.startsWith(digits) }) return WORD_PREFIX
        if (index.full.contains(digits)) return CONTAINS
        return NO_MATCH
    }

    fun score(label: String, digits: String): Int = score(index(label), digits)

    private val SEPARATORS = charArrayOf(
        ' ', '\t', '\n', '-', '_', '.', ',', ':', ';', '/', '\\',
        '(', ')', '[', ']', '{', '}', '&', '+', '\'', '"', '!', '?', '@', '#',
    )
}

/** A label's signatures, computed once. */
data class T9Index(val full: String, val words: List<String>)
