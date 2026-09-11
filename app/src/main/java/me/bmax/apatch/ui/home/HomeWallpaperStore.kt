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
        val imagePath = preferences.getString(KEY_FILE, null)
        val revision = preferences.getLong(KEY_REVISION, 0L)
        val crop = readCrop()

        if (!enabled) {
            return HomeWallpaperStateMapper.disabled(
                imagePath = imagePath,
                revision = revision,
                crop = crop,
            )
        }
        return resolveEnabledState(
            imagePath = imagePath,
            revision = revision,
            crop = crop,
        )
    }

    fun setEnabled(enabled: Boolean): HomeWallpaperState {
        val imagePath = preferences.getString(KEY_FILE, null)
        val revision = preferences.getLong(KEY_REVISION, 0L)
        val crop = readCrop()

        check(
            preferences.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .commit()
        ) {
            "Unable to persist Home wallpaper enabled state"
        }

        if (!enabled) {
            return HomeWallpaperStateMapper.disabled(
                imagePath = imagePath,
                revision = revision,
                crop = crop,
            )
        }
        return resolveEnabledState(
            imagePath = imagePath,
            revision = revision,
            crop = crop,
        )
    }

    fun importFrom(source: Uri): HomeWallpaperState {
        check(wallpaperDirectory.exists() || wallpaperDirectory.mkdirs()) {
            "Unable to create the Home wallpaper directory"
        }

        val target = File(wallpaperDirectory, FILE_NAME)
        val temporary = File(
            wallpaperDirectory,
            "$FILE_NAME.${System.nanoTime()}.tmp",
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

            val revision = System.currentTimeMillis()
            check(
                preferences.edit()
                    .putBoolean(KEY_ENABLED, true)
                    .putString(KEY_FILE, RELATIVE_FILE_PATH)
                    .putFloat(KEY_ZOOM, HomeWallpaperCrop.Default.zoom)
                    .putFloat(KEY_BIAS_X, HomeWallpaperCrop.Default.biasX)
                    .putFloat(KEY_BIAS_Y, HomeWallpaperCrop.Default.biasY)
                    .putLong(KEY_REVISION, revision)
                    .commit()
            ) {
                "Unable to persist the selected Home wallpaper"
            }

            return resolveEnabledState(
                imagePath = RELATIVE_FILE_PATH,
                revision = revision,
                crop = HomeWallpaperCrop.Default,
            )
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    fun saveCrop(crop: HomeWallpaperCrop) {
        val normalized = crop.normalized()
        check(
            preferences.edit()
                .putFloat(KEY_ZOOM, normalized.zoom)
                .putFloat(KEY_BIAS_X, normalized.biasX)
                .putFloat(KEY_BIAS_Y, normalized.biasY)
                .commit()
        ) {
            "Unable to persist the Home wallpaper crop"
        }
    }

    fun remove(): HomeWallpaperState {
        val storedFile = resolveStoredFile(preferences.getString(KEY_FILE, null))
        if (storedFile?.exists() == true) {
            check(storedFile.delete()) {
                "Unable to remove the stored Home wallpaper"
            }
        }
        check(
            preferences.edit()
                .remove(KEY_ENABLED)
                .remove(KEY_FILE)
                .remove(KEY_ZOOM)
                .remove(KEY_BIAS_X)
                .remove(KEY_BIAS_Y)
                .remove(KEY_REVISION)
                .commit()
        ) {
            "Unable to clear the Home wallpaper settings"
        }
        return HomeWallpaperState()
    }

    private fun resolveEnabledState(
        imagePath: String?,
        revision: Long,
        crop: HomeWallpaperCrop,
    ): HomeWallpaperState {
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

    private fun readCrop(): HomeWallpaperCrop = HomeWallpaperCrop(
        zoom = preferences.getFloat(KEY_ZOOM, HomeWallpaperCrop.Default.zoom),
        biasX = preferences.getFloat(KEY_BIAS_X, HomeWallpaperCrop.Default.biasX),
        biasY = preferences.getFloat(KEY_BIAS_Y, HomeWallpaperCrop.Default.biasY),
    ).normalized()

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

    private companion object {
        const val DIRECTORY_NAME = "home_wallpaper"
        const val FILE_NAME = "wallpaper.image"
        const val RELATIVE_FILE_PATH = "$DIRECTORY_NAME/$FILE_NAME"
        const val KEY_ENABLED = "home_wallpaper_enabled"
        const val KEY_FILE = "home_wallpaper_file"
        const val KEY_ZOOM = "home_wallpaper_zoom"
        const val KEY_BIAS_X = "home_wallpaper_bias_x"
        const val KEY_BIAS_Y = "home_wallpaper_bias_y"
        const val KEY_REVISION = "home_wallpaper_revision"
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val MAX_WALLPAPER_BYTES = 64L * 1024L * 1024L
    }
}
