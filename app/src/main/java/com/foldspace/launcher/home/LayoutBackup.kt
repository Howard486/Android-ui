package com.foldspace.launcher.home

/**
 * One item as it appears in a backup file.
 *
 * Deliberately not `HomeItemEntity`: a backup has to survive a schema change,
 * and database row ids mean nothing on another device. Folder membership
 * travels as the folder's *title* rather than its id, for the same reason.
 */
data class BackupItem(
    val surface: String,
    val posture: String,
    val pageIndex: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val type: String,
    val packageName: String? = null,
    val className: String? = null,
    val folderTitle: String? = null,
    /** The title of the folder holding this item, if any. */
    val inFolder: String? = null,
)

data class BackupPage(
    val surface: String,
    val posture: String,
    val pageIndex: Int,
    val kind: String,
    val contexts: String,
)

data class LayoutBackup(
    val version: Int = FORMAT_VERSION,
    val items: List<BackupItem>,
    val pages: List<BackupPage>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

/**
 * Reads and writes the backup file.
 *
 * A line-based text format rather than JSON: no dependency, and — the
 * property that actually matters — a corrupt line is skippable. Restoring
 * 95% of an arrangement beats refusing the whole file over one malformed
 * row, which is what a strict parser would do.
 *
 * Widgets are not backed up. An `appWidgetId` binds this device's widget host
 * to one provider instance; carrying the number to another device, or even
 * across a reinstall, would point at nothing. The restore reports how many
 * were dropped rather than quietly losing them.
 */
object LayoutBackupCodec {

    private const val FIELD = "\u001F"
    private const val HEADER = "foldspace-layout"

    fun encode(backup: LayoutBackup): String = buildString {
        append(HEADER).append(FIELD).append(backup.version).append('\n')
        backup.pages.forEach { page ->
            append(
                listOf(
                    "P",
                    page.surface,
                    page.posture,
                    page.pageIndex.toString(),
                    page.kind,
                    page.contexts,
                ).joinToString(FIELD),
            ).append('\n')
        }
        backup.items.forEach { item ->
            append(
                listOf(
                    "I",
                    item.surface,
                    item.posture,
                    item.pageIndex.toString(),
                    item.cellX.toString(),
                    item.cellY.toString(),
                    item.spanX.toString(),
                    item.spanY.toString(),
                    item.type,
                    item.packageName.orEmpty(),
                    item.className.orEmpty(),
                    // A folder title is user-typed, so it is the one field
                    // that could contain the separator and corrupt the line.
                    item.folderTitle.orEmpty().sanitised(),
                    item.inFolder.orEmpty().sanitised(),
                ).joinToString(FIELD),
            ).append('\n')
        }
    }

    /** Null only when the file is not one of ours; bad lines are skipped. */
    fun decode(raw: String): LayoutBackup? {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.split(FIELD) ?: return null
        if (header.firstOrNull() != HEADER) return null
        val version = header.getOrNull(1)?.toIntOrNull() ?: return null

        val items = mutableListOf<BackupItem>()
        val pages = mutableListOf<BackupPage>()

        for (line in lines.drop(1)) {
            val parts = line.split(FIELD)
            when (parts.firstOrNull()) {
                "P" -> decodePage(parts)?.let(pages::add)
                "I" -> decodeItem(parts)?.let(items::add)
                else -> Unit
            }
        }
        return LayoutBackup(version = version, items = items, pages = pages)
    }

    private fun String.sanitised(): String = filter { it.code >= 0x20 }

    private fun decodePage(parts: List<String>): BackupPage? {
        if (parts.size < 6) return null
        return BackupPage(
            surface = parts[1].ifBlank { return null },
            posture = parts[2].ifBlank { return null },
            pageIndex = parts[3].toIntOrNull() ?: return null,
            kind = parts[4].ifBlank { return null },
            contexts = parts[5],
        )
    }

    private fun decodeItem(parts: List<String>): BackupItem? {
        if (parts.size < 13) return null
        return BackupItem(
            surface = parts[1].ifBlank { return null },
            posture = parts[2].ifBlank { return null },
            pageIndex = parts[3].toIntOrNull() ?: return null,
            cellX = parts[4].toIntOrNull() ?: return null,
            cellY = parts[5].toIntOrNull() ?: return null,
            spanX = parts[6].toIntOrNull()?.coerceAtLeast(1) ?: 1,
            spanY = parts[7].toIntOrNull()?.coerceAtLeast(1) ?: 1,
            type = parts[8].ifBlank { return null },
            packageName = parts[9].takeIf { it.isNotBlank() },
            className = parts[10].takeIf { it.isNotBlank() },
            folderTitle = parts[11].takeIf { it.isNotBlank() },
            inFolder = parts[12].takeIf { it.isNotBlank() },
        )
    }
}

/** What a restore actually managed, so the UI can report it rather than redraw. */
data class RestoreOutcome(
    val itemsRestored: Int,
    val appsMissing: Int,
    val widgetsDropped: Int,
) {
    fun describe(): String = buildString {
        append("已還原 ").append(itemsRestored).append(" 個項目")
        if (appsMissing > 0) append("，").append(appsMissing).append(" 個 App 未安裝已略過")
        if (widgetsDropped > 0) append("，小工具需重新新增")
    }
}
