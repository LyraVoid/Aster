package me.bmax.apatch.ui.home

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import me.bmax.apatch.APApplication

internal class HomeWallpaperStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences = APApplication.sharedPreferences
    private val wallpaperDirectory = File(appContext.filesDir, DIRECTORY_NAME)

    fun load(): HomeWallpaperState {
        val enabled = preferences.getBoolean(KEY_ENABLED, false)
        return HomeWallpaperState(
            enabled = enabled,
            light = readSlot(HomeWallpaperSlot.LIGHT, enabled),
            night = readSlot(HomeWallpaperSlot.NIGHT, enabled),
            nightEnabled = preferences.getBoolean(KEY_NIGHT_ENABLED, false),
        )
    }

    fun setEnabled(enabled: Boolean): HomeWallpaperState {
        check(
            preferences.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .commit()
        ) {
            "Unable to persist Home wallpaper enabled state"
        }
        return load()
    }

    /**
     * Switching the dark wallpaper off keeps the stored image, so the choice can be taken back
     * without picking the picture again.
     */
    fun setNightEnabled(enabled: Boolean): HomeWallpaperState {
        check(
            preferences.edit()
                .putBoolean(KEY_NIGHT_ENABLED, enabled)
                .commit()
        ) {
            "Unable to persist the dark Home wallpaper choice"
        }
        return load()
    }

    fun importFrom(source: Uri, slot: HomeWallpaperSlot): HomeWallpaperState {
        check(wallpaperDirectory.exists() || wallpaperDirectory.mkdirs()) {
            "Unable to create the Home wallpaper directory"
        }

        val keys = keys(slot)
        val target = File(wallpaperDirectory, keys.fileName)
        val temporary = File(
            wallpaperDirectory,
            "${keys.fileName}.${System.nanoTime()}.tmp",
        )

        try {
            val input = checkNotNull(appContext.contentResolver.openInputStream(source)) {
                "Unable to open the selected Home wallpaper"
            }
            BufferedInputStream(input).use { bufferedInput ->
                FileOutputStream(temporary).use { output ->
                    copyWithLimit(bufferedInput, output)
                    output.fd.sync()
                }
            }

            check(readImageInfo(temporary) != null) {
                "The selected file is not a readable image"
            }
            Os.rename(temporary.absolutePath, target.absolutePath)

            val editor = preferences.edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(keys.fileKey, keys.relativePath)
                .putFloat(keys.zoomKey, HomeWallpaperCrop.Default.zoom)
                .putFloat(keys.biasXKey, HomeWallpaperCrop.Default.biasX)
                .putFloat(keys.biasYKey, HomeWallpaperCrop.Default.biasY)
                .putLong(keys.revisionKey, System.currentTimeMillis())
            if (slot == HomeWallpaperSlot.NIGHT) {
                // Choosing a picture for the dark theme says what it is for, so the feature switches
                // itself on. The switch stays there to turn it back off.
                editor.putBoolean(KEY_NIGHT_ENABLED, true)
            }
            check(editor.commit()) {
                "Unable to persist the selected Home wallpaper"
            }

            return load()
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    fun saveCrop(crop: HomeWallpaperCrop, slot: HomeWallpaperSlot) {
        val normalized = crop.normalized()
        val keys = keys(slot)
        check(
            preferences.edit()
                .putFloat(keys.zoomKey, normalized.zoom)
                .putFloat(keys.biasXKey, normalized.biasX)
                .putFloat(keys.biasYKey, normalized.biasY)
                .commit()
        ) {
            "Unable to persist the Home wallpaper crop"
        }
    }

    /**
     * Removing the light wallpaper removes the feature, so the dark one goes with it. Removing the
     * dark one only puts the dark theme back on the light image.
     */
    fun remove(slot: HomeWallpaperSlot): HomeWallpaperState {
        val removed = when (slot) {
            HomeWallpaperSlot.LIGHT -> HomeWallpaperSlot.entries.toList()
            HomeWallpaperSlot.NIGHT -> listOf(HomeWallpaperSlot.NIGHT)
        }
        removed.forEach(::deleteStoredFile)

        val editor = preferences.edit()
            .remove(KEY_NIGHT_ENABLED)
        removed.forEach { removedSlot ->
            val keys = keys(removedSlot)
            editor.remove(keys.fileKey)
                .remove(keys.zoomKey)
                .remove(keys.biasXKey)
                .remove(keys.biasYKey)
                .remove(keys.revisionKey)
        }
        if (slot == HomeWallpaperSlot.LIGHT) {
            editor.remove(KEY_ENABLED)
        }
        check(editor.commit()) {
            "Unable to clear the Home wallpaper settings"
        }
        return load()
    }

    private fun deleteStoredFile(slot: HomeWallpaperSlot) {
        val storedFile = resolveStoredFile(preferences.getString(keys(slot).fileKey, null))
        if (storedFile?.exists() == true) {
            check(storedFile.delete()) {
                "Unable to remove the stored Home wallpaper"
            }
        }
    }

    private fun readSlot(slot: HomeWallpaperSlot, enabled: Boolean): HomeWallpaperSlotState {
        val keys = keys(slot)
        val imagePath = preferences.getString(keys.fileKey, null)
        val revision = preferences.getLong(keys.revisionKey, 0L)
        val crop = readCrop(slot)
        if (!enabled) {
            return HomeWallpaperStateMapper.disabled(
                imagePath = imagePath,
                revision = revision,
                crop = crop,
            )
        }
        val storedFile = resolveStoredFile(imagePath)
        val exists = storedFile?.isFile == true && storedFile.length() > 0L
        val info = storedFile?.takeIf { exists }?.let(::readImageInfo)
        return HomeWallpaperStateMapper.resolveEnabled(
            imagePath = imagePath,
            revision = revision,
            crop = crop,
            fileExists = exists,
            fileInfo = info,
        )
    }

    private fun resolveStoredFile(path: String?): File? =
        HomeWallpaperFiles.resolve(appContext.filesDir, path)

    private fun readCrop(slot: HomeWallpaperSlot): HomeWallpaperCrop {
        val keys = keys(slot)
        return HomeWallpaperCrop(
            zoom = preferences.getFloat(keys.zoomKey, HomeWallpaperCrop.Default.zoom),
            biasX = preferences.getFloat(keys.biasXKey, HomeWallpaperCrop.Default.biasX),
            biasY = preferences.getFloat(keys.biasYKey, HomeWallpaperCrop.Default.biasY),
        ).normalized()
    }

    private fun readImageInfo(file: File): HomeWallpaperFileInfo? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return HomeWallpaperFileInfo(
            width = options.outWidth,
            height = options.outHeight,
        ).takeIf { it.width > 0 && it.height > 0 }
    }

    private fun copyWithLimit(
        input: BufferedInputStream,
        output: FileOutputStream,
    ) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            copied += read
            check(copied <= MAX_WALLPAPER_BYTES) {
                "The selected Home wallpaper is too large"
            }
            output.write(buffer, 0, read)
        }
        check(copied > 0L) {
            "The selected Home wallpaper is empty"
        }
    }

    /**
     * The keys of the light wallpaper keep the names they have always had, so an existing selection
     * is picked up unchanged; only the dark one is new.
     */
    private data class SlotKeys(
        val fileName: String,
        val relativePath: String,
        val fileKey: String,
        val zoomKey: String,
        val biasXKey: String,
        val biasYKey: String,
        val revisionKey: String,
    )

    private fun keys(slot: HomeWallpaperSlot): SlotKeys = when (slot) {
        HomeWallpaperSlot.LIGHT -> LIGHT_KEYS
        HomeWallpaperSlot.NIGHT -> NIGHT_KEYS
    }

    private companion object {
        const val DIRECTORY_NAME = "home_wallpaper"
        const val KEY_ENABLED = "home_wallpaper_enabled"
        const val KEY_NIGHT_ENABLED = "home_wallpaper_night_enabled"
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val MAX_WALLPAPER_BYTES = 64L * 1024L * 1024L

        val LIGHT_KEYS = SlotKeys(
            fileName = "wallpaper.image",
            relativePath = "$DIRECTORY_NAME/wallpaper.image",
            fileKey = "home_wallpaper_file",
            zoomKey = "home_wallpaper_zoom",
            biasXKey = "home_wallpaper_bias_x",
            biasYKey = "home_wallpaper_bias_y",
            revisionKey = "home_wallpaper_revision",
        )

        val NIGHT_KEYS = SlotKeys(
            fileName = "wallpaper.night.image",
            relativePath = "$DIRECTORY_NAME/wallpaper.night.image",
            fileKey = "home_wallpaper_night_file",
            zoomKey = "home_wallpaper_night_zoom",
            biasXKey = "home_wallpaper_night_bias_x",
            biasYKey = "home_wallpaper_night_bias_y",
            revisionKey = "home_wallpaper_night_revision",
        )
    }
}
