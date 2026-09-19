package me.bmax.apatch.util

import android.content.Context
import android.os.Process
import android.system.Os
import android.util.Log
import androidx.core.content.edit
import me.bmax.apatch.APApplication
import java.io.File

private const val Tag = "RootServiceJar"

/** Whether the one-time look at the jar has already happened on this install. */
private const val SeenKey = "root_service_jar_seen"

/**
 * The file the root process is made of.
 *
 * libsu writes this jar into the app's own cache and starts the privileged process from it with
 * `app_process`. On some devices that file ends up in a state the platform refuses to load — the
 * class loader throws `SecurityException: Writable dex file ... is not allowed`, the virtual machine
 * aborts, and the process dies before it can serve anything. What the reader sees is a manager that
 * has lost root, with nothing on screen to say why.
 *
 * The file is therefore looked at before anything asks for a root service. On the first launch after
 * this existed it is removed whatever it looks like, because a bad one left by an older install is
 * exactly the case this is for and there is no way to tell it apart from a good one by inspection —
 * after that only a file that looks wrong is replaced. What was found is logged either way: a report
 * of this cannot be acted on without that file's mode, and nothing else in the app records it.
 */
internal fun prepareRootServiceJar(context: Context) {
    // Both caches, because libsu writes into whichever one the context it was handed points at, and
    // on a device that has not been unlocked that is the device protected one. Looking at only one
    // of them is how a jar the platform refuses can sit there unexamined.
    val caches = listOfNotNull(
        context.cacheDir,
        runCatching { context.createDeviceProtectedStorageContext().cacheDir }.getOrNull(),
    ).distinct()

    caches.forEach { inspectRootJar(it) }
}

private fun inspectRootJar(cacheDir: File) {
    val jar = File(cacheDir, "main.jar")
    if (!jar.isFile) {
        // Nothing to judge here, and the one-time look is not spent on nothing: the shell writes the
        // jar the first time a root service is asked for, and that is when it can be judged.
        Log.i(Tag, "${cacheDir.name} of ${cacheDir.parentFile?.name}: no root service jar yet")
        return
    }

    val status = runCatching { Os.stat(jar.absolutePath) }.getOrNull()
    if (status == null) {
        Log.w(Tag, "could not read the root service jar's own state; leaving it alone")
        return
    }

    val mode = status.st_mode and 0x1FF
    val appUid = Process.myUid()
    val wrong = rootJarLooksWrong(mode = mode, uid = status.st_uid, appUid = appUid)
    val seen = APApplication.sharedPreferences.getBoolean(SeenKey, false)

    Log.i(
        Tag,
        "root service jar %s: mode=0%o uid=%d app=%d size=%d seen=%s%s".format(
            jar.absolutePath,
            mode,
            status.st_uid,
            appUid,
            jar.length(),
            seen,
            if (wrong) " wrong" else "",
        ),
    )

    if (!wrong && seen) return

    APApplication.sharedPreferences.edit { putBoolean(SeenKey, true) }
    Log.i(Tag, if (wrong) "root service jar looks wrong; replacing it" else "first look; replacing it")
    // A fresh write keeps whatever mode the old file had, so this is the only way back to a good
    // one: the file has to go, not be overwritten.
    if (!jar.delete()) {
        Log.w(Tag, "could not remove the root service jar; the next root service may not start")
    }
}

/**
 * Whether a jar in this state is one the platform may refuse to load.
 *
 * A file that group or other can write, one its owner cannot read, and one that is not this app's
 * are all states no fresh write produces, so they are treated as unusable rather than reasoned
 * about. The mode is the low nine bits of the stat mode.
 */
internal fun rootJarLooksWrong(mode: Int, uid: Int, appUid: Int): Boolean =
    (mode and 0x12) != 0 ||      // writable by group or by other
        (mode and 0x100) == 0 || // not readable by its owner
        uid != appUid
