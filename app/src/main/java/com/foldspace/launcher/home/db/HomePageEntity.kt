package com.foldspace.launcher.home.db

import androidx.room.Entity
import androidx.room.Index

/**
 * Page metadata.
 *
 * Only pages that are *not* an ordinary app grid need a row here — the grid
 * pages are implied by the items on them. A feed page or a work page has no
 * items, so without this table an empty page would be indistinguishable from
 * a page that does not exist.
 */
@Entity(
    tableName = "home_pages",
    primaryKeys = ["spaceKey", "postureKey", "pageIndex"],
    indices = [Index(value = ["spaceKey", "postureKey"])],
)
data class HomePageEntity(
    val spaceKey: String,
    val postureKey: String,
    val pageIndex: Int,
    val kind: String,
)
