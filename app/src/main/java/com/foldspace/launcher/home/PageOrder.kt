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
