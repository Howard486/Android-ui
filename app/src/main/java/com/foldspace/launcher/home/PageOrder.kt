package com.foldspace.launcher.home

/**
 * Where each page ends up when the user rearranges them.
 *
 * Kept as pure arithmetic and away from the database on purpose. Reordering
 * pages rewrites a column that is simultaneously part of `home_pages`'
 * composite primary key and of `home_items`' unique index, so the SQL has to
 * be a two-phase park — and a permutation bug hiding inside that transaction
 * would be very hard to see. The permutation is decided here, where it can be
 * checked.
 *
 * Every function returns a list whose *index is the new page number* and whose
 * *value is the old page number*. That direction is the one the writer wants:
 * it iterates new positions and asks what belongs there.
 */
object PageOrder {

    /**
     * Moves one page, shifting the pages between the two ends along by one.
     *
     * Out-of-range arguments give back the identity rather than throwing: this
     * is driven by a drag, and a drag that ends somewhere impossible should
     * leave the arrangement alone.
     */
    fun move(count: Int, from: Int, to: Int): List<Int> {
        if (count <= 0) return emptyList()
        val identity = (0 until count).toList()
        if (from !in 0 until count || to !in 0 until count || from == to) return identity

        return identity.toMutableList().apply {
            add(to, removeAt(from))
        }
    }

    /**
     * Moving the page stored at [fromIndex] to where [toIndex] sits, as
     * `(oldStoredIndex → newStoredIndex)` pairs.
     *
     * This exists because the overview counts *positions* and the database
     * stores *indices*, and those are only the same list when the stored
     * indices happen to run 0, 1, 2 with no gaps. A page hidden by the current
     * context is filtered out of the list while keeping its stored index, so
     * every page after it is off by one — and the reorder then wrote
     * `WHERE pageIndex = ...` against a page that does not exist, which is
     * silently nothing at all.
     *
     * Working through the real index set keeps the set unchanged and only
     * reorders what sits in it, so no gap is ever introduced either.
     */
    fun moveWithin(indices: List<Int>, fromIndex: Int, toIndex: Int): List<Pair<Int, Int>> {
        val sorted = indices.distinct().sorted()
        val from = sorted.indexOf(fromIndex)
        val to = sorted.indexOf(toIndex)
        if (from < 0 || to < 0 || from == to) return emptyList()
        return changes(move(sorted.size, from, to))
            .map { (oldPosition, newPosition) -> sorted[oldPosition] to sorted[newPosition] }
    }

    /** Drops one page; everything after it shifts down by one. */
    fun removed(count: Int, index: Int): List<Int> {
        if (count <= 0) return emptyList()
        val identity = (0 until count).toList()
        if (index !in 0 until count) return identity
        return identity.filterNot { it == index }
    }

    /**
     * The moves the writer actually has to make, as (oldIndex -> newIndex),
     * with pages that did not move left out.
     */
    fun changes(order: List<Int>): List<Pair<Int, Int>> =
        order.withIndex()
            .filter { (newIndex, oldIndex) -> newIndex != oldIndex }
            .map { (newIndex, oldIndex) -> oldIndex to newIndex }
}
