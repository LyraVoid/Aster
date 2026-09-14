package me.bmax.apatch

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.topjohnwu.superuser.CallbackList
import me.bmax.apatch.ui.CrashHandleActivity
import me.bmax.apatch.root.RootCapabilityDetails
import me.bmax.apatch.root.RootCapabilityRepository
import me.bmax.apatch.root.RootCheckError
import me.bmax.apatch.root.RootCheckPhase
import me.bmax.apatch.root.RootDetailRead
import me.bmax.apatch.root.RootDetailState
import me.bmax.apatch.root.RootInitializationSnapshot
import me.bmax.apatch.root.toRootDetailRead
import me.bmax.apatch.ui.theme.CustomFont
import me.bmax.apatch.util.APatchCli
import me.bmax.apatch.util.APatchKeyHelper
import me.bmax.apatch.util.ApdVersionResult
import me.bmax.apatch.util.InstalledApdState
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.getRootShell
import me.bmax.apatch.util.resolveInstalledApdState
import me.bmax.apatch.util.rootShellForResult
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.exitProcess

lateinit var apApp: APApplication

const val TAG = "APatch"

class APApplication : Application(), Thread.UncaughtExceptionHandler {
    lateinit var okhttpClient: OkHttpClient

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    enum class State {
        UNKNOWN_STATE,

        KERNELPATCH_INSTALLED, KERNELPATCH_NEED_UPDATE, KERNELPATCH_NEED_REBOOT, KERNELPATCH_UNINSTALLING,

        ANDROIDPATCH_NOT_INSTALLED, ANDROIDPATCH_INSTALLED, ANDROIDPATCH_INSTALLING, ANDROIDPATCH_NEED_UPDATE, ANDROIDPATCH_UNINSTALLING,
    }


    companion object {
        const val APD_PATH = "/data/adb/apd"

        @Deprecated("No more KPatch ELF from 0.11.0-dev")
        const val KPATCH_PATH = "/data/adb/kpatch"
        const val SUPERCMD = "/system/bin/truncate"
        const val APATCH_FOLDER = "/data/adb/ap/"
        private const val APATCH_BIN_FOLDER = APATCH_FOLDER + "bin/"
        private const val APATCH_LOG_FOLDER = APATCH_FOLDER + "log/"
        private const val APD_LINK_PATH = APATCH_BIN_FOLDER + "apd"
        const val PACKAGE_CONFIG_FILE = APATCH_FOLDER + "package_config"
        const val SU_PATH_FILE = APATCH_FOLDER + "su_path"
        const val SAFEMODE_FILE = "/dev/.safemode"
        private const val NEED_REBOOT_FILE = "/dev/.need_reboot"
        /**
         * Longest stack trace handed to the crash screen. A run-away recursion repeats one frame
         * thousands of times, and a trace that large cannot cross the binder limit, so the report
         * would fail and the app would disappear without a word.
         */
        private const val MAX_CRASH_TRACE_LENGTH = 64 * 1024
        const val MAGIC_MOUNT_FILE = "/data/adb/.magic_mount_enable"
        const val GLOBAL_NAMESPACE_FILE = "/data/adb/.global_namespace_enable"
        const val SUCOMPAT_FILE = "/data/adb/ap/sucompat"
        const val SELINUX_HIDE_FILE = APATCH_FOLDER + "selinux_hide"
        const val JAILBREAK_FILE = APATCH_FOLDER + "jailbreak"
        const val JAILBREAK_KO_PATH = APATCH_FOLDER + "kernelpatch.ko"
        /** Persisted, file-backed KPMs. Each module lives in <id>/<id>.kpm. */
        const val KPMS_DIR = APATCH_FOLDER + "kpm/"

        @Deprecated("Use 'apd -V'")
        const val APATCH_VERSION_PATH = APATCH_FOLDER + "version"
        private const val MAGISKPOLICY_BIN_PATH = APATCH_BIN_FOLDER + "magiskpolicy"
        private const val BUSYBOX_BIN_PATH = APATCH_BIN_FOLDER + "busybox"
        private const val RESETPROP_BIN_PATH = APATCH_BIN_FOLDER + "resetprop"
        private const val KPTOOLS_BIN_PATH = APATCH_BIN_FOLDER + "kptools"
        const val DEFAULT_SCONTEXT = "u:r:untrusted_app:s0"
        const val MAGISK_SCONTEXT = "u:r:magisk:s0"

        private const val DEFAULT_SU_PATH = "/system/bin/kp"
        private const val LEGACY_SU_PATH = "/system/bin/su"

        const val SP_NAME = "config"
        private const val SHOW_BACKUP_WARN = "show_backup_warning"
        private const val CONFIRM_MODULE_INSTALL = "confirm_module_install"
        private const val SHOW_SWITCH_INDICATOR = "show_switch_indicator"
        private const val STAY_ON_ACTION_PAGE = "stay_on_action_page"
        lateinit var sharedPreferences: SharedPreferences

        private val logCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                Log.d(TAG, s.toString())
            }
        }

        private val _kpStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val kpStateLiveData: LiveData<State> = _kpStateLiveData

        private val _apStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val apStateLiveData: LiveData<State> = _apStateLiveData

        private val rootInitializationSession = AtomicLong(0L)
        private val _rootInitializationLiveData = MutableLiveData(RootInitializationSnapshot())
        val rootInitializationLiveData: LiveData<RootInitializationSnapshot> =
            _rootInitializationLiveData

        @Suppress("DEPRECATION")
        fun uninstallApatch() {
            if (_apStateLiveData.value != State.ANDROIDPATCH_INSTALLED) return
            _apStateLiveData.value = State.ANDROIDPATCH_UNINSTALLING

            Natives.resetSuPath(DEFAULT_SU_PATH)

            val cmds = arrayOf(
                "rm -f $APD_PATH",
                "rm -f $KPATCH_PATH",
                "rm -rf $APATCH_BIN_FOLDER",
                "rm -rf $APATCH_LOG_FOLDER",
                "rm -rf $APATCH_VERSION_PATH",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            Log.d(TAG, "APatch uninstalled...")
            if (_kpStateLiveData.value == State.UNKNOWN_STATE) {
                _apStateLiveData.postValue(State.UNKNOWN_STATE)
            } else {
                _apStateLiveData.postValue(State.ANDROIDPATCH_NOT_INSTALLED)
            }
        }

        @Suppress("DEPRECATION")
        fun installApatch() {
            val state = _apStateLiveData.value
            if (state != State.ANDROIDPATCH_NOT_INSTALLED && state != State.ANDROIDPATCH_NEED_UPDATE) {
                return
            }
            _apStateLiveData.value = State.ANDROIDPATCH_INSTALLING
            val nativeDir = apApp.applicationInfo.nativeLibraryDir

            Natives.resetSuPath(LEGACY_SU_PATH)

            val cmds = arrayOf(
                "mkdir -p $APATCH_BIN_FOLDER",
                "mkdir -p $APATCH_LOG_FOLDER",

                "cp -f ${nativeDir}/libapd.so $APD_PATH",
                "chmod +x $APD_PATH",
                "ln -s $APD_PATH $APD_LINK_PATH",
                "restorecon $APD_PATH",

                "rm -f $MAGISKPOLICY_BIN_PATH",
                "ln -s $APD_PATH $MAGISKPOLICY_BIN_PATH",
                "rm -f $RESETPROP_BIN_PATH",
                "ln -s $APD_PATH $RESETPROP_BIN_PATH",
               
                "cp -f ${nativeDir}/libbusybox.so $BUSYBOX_BIN_PATH",
                "chmod +x $BUSYBOX_BIN_PATH",
                "cp -f ${nativeDir}/libkptools.so $KPTOOLS_BIN_PATH",
                "chmod +x $KPTOOLS_BIN_PATH",



                "touch $PACKAGE_CONFIG_FILE",
                "touch $SU_PATH_FILE",
                "[ -s $SU_PATH_FILE ] || echo $LEGACY_SU_PATH > $SU_PATH_FILE",
                "echo ${Version.getManagerVersion().second} > $APATCH_VERSION_PATH",
                "restorecon -R $APATCH_FOLDER",

                "${nativeDir}/libmagiskpolicy.so --magisk --live",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            // clear shell cache
            APatchCli.refresh()

            Log.d(TAG, "APatch installed...")
            _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
        }

        fun markNeedReboot() {
            val result = rootShellForResult("touch $NEED_REBOOT_FILE")
            _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
            Log.d(TAG, "mark reboot ${result.code}")
        }


        var superKey: String = ""
            set(value) {
                field = value
                val sessionId = rootInitializationSession.incrementAndGet()
                _rootInitializationLiveData.value = RootInitializationSnapshot(
                    phase = RootCheckPhase.CHECKING,
                    sessionId = sessionId,
                    startedAt = SystemClock.elapsedRealtime(),
                )

                val ready = runCatching {
                    Natives.nativeReady(value)
                }.getOrElse {
                    _kpStateLiveData.value = State.UNKNOWN_STATE
                    _apStateLiveData.value = State.UNKNOWN_STATE
                    completeRootInitialization(
                        sessionId = sessionId,
                        phase = RootCheckPhase.FAILED,
                        error = RootCheckError.NATIVE_CHECK_FAILED,
                    )
                    return
                }
                _kpStateLiveData.value =
                    if (ready) State.KERNELPATCH_INSTALLED else State.UNKNOWN_STATE
                _apStateLiveData.value =
                    if (ready) State.ANDROIDPATCH_NOT_INSTALLED else State.UNKNOWN_STATE
                Log.d(TAG, "state: " + _kpStateLiveData.value)
                if (!ready) {
                    completeRootInitialization(
                        sessionId = sessionId,
                        phase = RootCheckPhase.READY,
                        kernelPatchDetected = false,
                        rootProbeSucceeded = false,
                    )
                    return
                }

                thread {
                    try {
                        val rc = Natives.su(0, null)
                        if (!rc) {
                            Log.e(TAG, "Native.su failed")
                            completeRootInitialization(
                                sessionId = sessionId,
                                phase = RootCheckPhase.READY,
                                kernelPatchDetected = true,
                                rootProbeSucceeded = false,
                                error = RootCheckError.ROOT_PROBE_FAILED,
                            )
                            return@thread
                        }

                        // KernelPatch version
                        //val buildV = Version.buildKPVUInt()
                        //val installedV = Version.installedKPVUInt()
                        //use build time to check update
                        val buildV = Version.getKpImg()
                        val installedV = Version.installedKPTime()


                        Log.d(TAG, "kp installed version: ${installedV}, build version: $buildV")

                        // use != instead of > to enable downgrade,
                        if (buildV != installedV) {
                            _kpStateLiveData.postValue(State.KERNELPATCH_NEED_UPDATE)
                        }
                        Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                        if (File(NEED_REBOOT_FILE).exists()) {
                            _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
                        }
                        Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                        val suPathRead = runCatching {
                            Natives.suPathResult().toRootDetailRead()
                        }.getOrElse {
                            RootDetailRead<String>(state = RootDetailState.ERROR)
                        }

                        // AndroidPatch version
                        val mgv = Version.getManagerVersion().second
                        val apdVersionResult = runCatching {
                            Version.probeInstalledApdVersion()
                        }.getOrElse {
                            ApdVersionResult.Error(-1)
                        }
                        val apdVersionRead = apdVersionResult.toRootDetailRead()
                        val installedApdVInt = Version.updateInstalledApdVersion(apdVersionResult)
                        Log.d(
                            TAG,
                            "manager version: $mgv, installed apd version: $installedApdVInt"
                        )

                        // The installed patch is compared with the one this manager ships rather
                        // than with the manager version code, which moves on every build.
                        val bundledApdSha256 = Version.getBundledApdSha256()
                        val installedApdSha256 = Version.getInstalledApdSha256()
                        Log.d(
                            TAG,
                            "bundled apd sha256: $bundledApdSha256, " +
                                "installed apd sha256: $installedApdSha256"
                        )

                        when (
                            resolveInstalledApdState(
                                bundledSha256 = bundledApdSha256,
                                installedSha256 = installedApdSha256,
                                installedVersion = installedApdVInt,
                            )
                        ) {
                            InstalledApdState.NOT_INSTALLED ->
                                _apStateLiveData.postValue(State.ANDROIDPATCH_NOT_INSTALLED)

                            InstalledApdState.INSTALLED ->
                                _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)

                            InstalledApdState.NEED_UPDATE -> {
                                _apStateLiveData.postValue(State.ANDROIDPATCH_NEED_UPDATE)
                                // su path
                                val suPathFile = File(SU_PATH_FILE)
                                if (suPathFile.exists()) {
                                    val suPath = suPathFile.readLines()[0].trim()
                                    if (
                                        suPathRead.state == RootDetailState.AVAILABLE &&
                                        suPathRead.value != suPath
                                    ) {
                                        Log.d(TAG, "su path: $suPath")
                                        Natives.resetSuPath(suPath)
                                    }
                                }
                            }
                        }
                        Log.d(TAG, "ap state: " + _apStateLiveData.value)

                        completeRootInitialization(
                            sessionId = sessionId,
                            phase = RootCheckPhase.READY,
                            kernelPatchDetected = true,
                            rootProbeSucceeded = true,
                            details = RootCapabilityDetails(
                                suPath = suPathRead.value,
                                suPathState = suPathRead.state,
                                androidPatchVersion = apdVersionRead.value,
                                androidPatchVersionState = apdVersionRead.state,
                            ),
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "Root capability initialization failed", t)
                        completeRootInitialization(
                            sessionId = sessionId,
                            phase = RootCheckPhase.FAILED,
                            error = RootCheckError.UNEXPECTED,
                        )
                    }
                }
            }

        private fun completeRootInitialization(
            sessionId: Long,
            phase: RootCheckPhase,
            kernelPatchDetected: Boolean? = null,
            rootProbeSucceeded: Boolean? = null,
            details: RootCapabilityDetails = RootCapabilityDetails(),
            error: RootCheckError? = null,
        ) {
            val current = _rootInitializationLiveData.value ?: return
            val currentSession = current.sessionId
            if (currentSession != sessionId) {
                return
            }
            _rootInitializationLiveData.postValue(RootInitializationSnapshot(
                phase = phase,
                sessionId = sessionId,
                kernelPatchDetected = kernelPatchDetected,
                rootProbeSucceeded = rootProbeSucceeded,
                startedAt = current.startedAt,
                completedAt = SystemClock.elapsedRealtime(),
                details = details,
                error = error,
            ))
        }

        /**
         * Resolve the SuperKey used to authenticate against the running kernel.
         *
         * The new manager defaults to "su" (implicit signature/uid authorization).
         * Kernels patched by legacy versions, however, were patched with a real
         * random/custom SuperKey and know nothing about signature authorization,
         * so "su" fails for users who upgraded from such a version. To keep the
         * original SuperKey upgrade path working, fall back to the legacy SuperKey
         * persisted (Keystore-encrypted) by older managers and use it to elevate,
         * letting the user upgrade the kernel to the latest signature-authorized one.
         *
         * Once "su" succeeds the kernel no longer relies on a SuperKey, so any
         * stale legacy key is cleared.
         */
        private fun resolveSuperKey(): String {
            APatchKeyHelper.setSharedPreferences(sharedPreferences)
            val savedKey = APatchKeyHelper.readSPSuperKey()

            // Signature authorization (new default).
            if (Natives.nativeReady("su")) {
                if (!savedKey.isNullOrEmpty()) {
                    APatchKeyHelper.clearConfigKey()
                    Log.i(TAG, "signature auth ready, cleared legacy SuperKey")
                }
                return "su"
            }

            // Legacy kernel patched with a real SuperKey: reuse the stored one.
            if (!savedKey.isNullOrEmpty() && Natives.nativeReady(savedKey)) {
                Log.i(TAG, "fallback to legacy stored SuperKey for upgrade")
                return savedKey
            }

            return "su"
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The app-zygote for the jailbreak MagicaService runs without a UserManager,
        // so shared prefs and other context-dependent setup are unavailable there.
        // AppZygotePreload drives the jailbreak via JNI directly; skip init here.
        if (getSystemService(Context.USER_SERVICE) == null) {
            return
        }
        apApp = this

        val isArm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        if (!isArm64) {
            Toast.makeText(applicationContext, "Unsupported architecture!", Toast.LENGTH_LONG)
                .show()
            Thread.sleep(5000)
            exitProcess(0)
        }

        // TODO: We can't totally protect superkey from be stolen by root or LSPosed-like injection tools in user space, the only way is don't use superkey,
        // TODO: 1. make me root by kernel
        // TODO: 2. remove all usage of superkey
        sharedPreferences = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        RootCapabilityRepository.start()
        superKey = resolveSuperKey()
        // Reads the font the reader chose last time and loads it off the main thread, so the first
        // frame the theme draws already knows whether there is one.
        CustomFont.load(this)

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "APatch/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()
    }

    fun getBackupWarningState(): Boolean {
        return sharedPreferences.getBoolean(SHOW_BACKUP_WARN, true)
    }

    fun updateBackupWarningState(state: Boolean) {
        sharedPreferences.edit { putBoolean(SHOW_BACKUP_WARN, state) }
    }

    /**
     * Whether an install names the file and waits for a yes before it unpacks anything. On by
     * default: the zip is about to be extracted into /data/adb/modules as root, and the file name
     * is often the only thing a reader can check it by.
     */
    fun getModuleInstallConfirmState(): Boolean {
        return sharedPreferences.getBoolean(CONFIRM_MODULE_INSTALL, true)
    }

    fun updateModuleInstallConfirmState(state: Boolean) {
        sharedPreferences.edit { putBoolean(CONFIRM_MODULE_INSTALL, state) }
    }

    /**
     * Whether the switches this app draws itself carry a status icon. On by default: the manager
     * asks the reader to turn things on and off in a lot of places, and the thumb is the one part
     * of a switch that is always in view.
     */
    fun getSwitchIndicatorState(): Boolean {
        return sharedPreferences.getBoolean(SHOW_SWITCH_INDICATOR, true)
    }

    fun updateSwitchIndicatorState(state: Boolean) {
        sharedPreferences.edit { putBoolean(SHOW_SWITCH_INDICATOR, state) }
    }

    /**
     * Whether a module's action leaves its log on screen when it succeeds, instead of going back to
     * the list. On by default: the output is the reason the action was run, and a page that closes
     * itself takes it away before it can be read.
     */
    fun getStayOnActionPageState(): Boolean {
        return sharedPreferences.getBoolean(STAY_ON_ACTION_PAGE, true)
    }

    fun updateStayOnActionPageState(state: Boolean) {
        sharedPreferences.edit { putBoolean(STAY_ON_ACTION_PAGE, state) }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val exceptionMessage = Log.getStackTraceString(e).take(MAX_CRASH_TRACE_LENGTH)
        val threadName = t.name
        Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")
        val intent = Intent(this, CrashHandleActivity::class.java).apply {
            putExtra("exception_message", exceptionMessage)
            putExtra("thread", threadName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        exitProcess(10)
    }
}
