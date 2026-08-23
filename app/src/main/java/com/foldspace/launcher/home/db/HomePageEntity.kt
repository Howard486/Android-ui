package com.foldspace.launcher.home.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Page metadata.
 *
 * Only pages that are *not* an ordinary app grid need a row here — the grid
 * pages are implied by the items on them. A feed page or a widget page has no
 * items, so without this table an empty page would be indistinguishable from
 * a page that does not exist.
 *
 * [contexts] is what makes the Focus model work: one arrangement, and a page
 * appears or does not appear depending on which 情境 is active. Empty means
 * every context shows it.
 */
@Entity(
    tableName = "home_pages",
    primaryKeys = ["surfaceKey", "postureKey", "pageIndex"],
    indices = [Index(value = ["surfaceKey", "postureKey"])],
)
data class HomePageEntity(
    val surfaceKey: String,
    val postureKey: String,
    val pageIndex: Int,
    val kind: String,

    /** Comma-separated Space keys; empty string means every context. */
    @ColumnInfo(defaultValue = "")
    val contexts: String = "",
)
