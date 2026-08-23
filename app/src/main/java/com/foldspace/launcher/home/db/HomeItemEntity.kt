package com.foldspace.launcher.home.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per placed thing.
 *
 * `container` follows the Launcher3 convention: [CONTAINER_DESKTOP] means the
 * item sits on a page and `pageIndex`/`cellX`/`cellY` are meaningful; any
 * other value is the id of the folder holding it, and `sortOrder` is what
 * matters instead.
 *
 * The unique index is what stops two items claiming one cell — a bug that is
 * otherwise invisible until the user sees one icon drawn on top of another.
 * Folder members are excluded from it by construction, since they all share
 * cell (-1,-1).
 *
 * Keyed on `surfaceKey`, not on a Space. 通用 and 工作 share one arrangement
 * and differ only in which pages they show; keeping a row per Space meant an
 * app placed in one context did not exist in the other.
 */
@Entity(
    tableName = "home_items",
    indices = [
        Index(value = ["surfaceKey", "postureKey", "pageIndex"]),
        Index(
            value = ["surfaceKey", "postureKey", "container", "pageIndex", "cellX", "cellY"],
            unique = true,
        ),
    ],
)
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val surfaceKey: String,
    val postureKey: String,

    @ColumnInfo(defaultValue = "-1")
    val container: Long = CONTAINER_DESKTOP,

    val pageIndex: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,

    /** Position inside a folder; ignored on the desktop. */
    val sortOrder: Int = 0,

    val itemType: String,

    // App
    val packageName: String? = null,
    val className: String? = null,
    val userSerial: Long? = null,

    // Folder
    val folderTitle: String? = null,

    // Widget
    val appWidgetId: Int? = null,
    val widgetProvider: String? = null,
) {
    companion object {
        const val CONTAINER_DESKTOP = -1L

        /** Folder members do not occupy a cell. */
        const val CELL_NONE = -1
    }
}
