package me.bmax.apatch.ui.home

import java.io.File

internal object HomeWallpaperFiles {
    fun resolve(filesDir: File, path: String?): File? {
        val normalized = path?.takeIf { it.isNotBlank() } ?: return null
        val root = filesDir.canonicalFile
        val file = File(root, normalized).canonicalFile
        return file.takeIf { it.path.startsWith(root.path + File.separator) }
    }
}
