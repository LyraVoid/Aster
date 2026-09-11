package me.bmax.apatch.ui.home

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeWallpaperFilesTest {
    @Test
    fun resolvesRelativePathInsideFilesDirectory() {
        val filesDir = Files.createTempDirectory("aster-wallpaper").toFile()

        val resolved = HomeWallpaperFiles.resolve(
            filesDir = filesDir,
            path = "home_wallpaper/wallpaper.image",
        )

        assertEquals(
            File(filesDir, "home_wallpaper/wallpaper.image").canonicalFile,
            resolved,
        )
    }

    @Test
    fun rejectsBlankPath() {
        val filesDir = Files.createTempDirectory("aster-wallpaper").toFile()

        assertNull(HomeWallpaperFiles.resolve(filesDir, null))
        assertNull(HomeWallpaperFiles.resolve(filesDir, ""))
        assertNull(HomeWallpaperFiles.resolve(filesDir, "   "))
    }

    @Test
    fun rejectsPathOutsideFilesDirectory() {
        val filesDir = Files.createTempDirectory("aster-wallpaper").toFile()

        assertNull(HomeWallpaperFiles.resolve(filesDir, "../outside.image"))
    }
}
