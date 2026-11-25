package com.arcturus.haven.launcher.bridge

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import android.os.UserHandle
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.arcturus.haven.HavenApplication
import com.arcturus.haven.services.IHavenService
import com.arcturus.haven.services.IStartActivityProxy
import com.arcturus.haven.ui.DummyActivity
import com.arcturus.haven.util.LocalStorageManager
import com.arcturus.haven.util.SettingsManager
import com.arcturus.haven.util.Utility
import java.lang.ref.WeakReference

enum class VaultProfileState {
    UNPROVISIONED,
    SETUP_PENDING,
    PROFILE_PAUSED,
    IDLE_DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

/**
 * Coordinates on-demand cross-profile binding to main and work [IHavenService] instances
 * without killing the main launcher process when backgrounded.
 */
object HavenProfileSession {
    private const val IDLE_DISCONNECT_DELAY_MS = 45_000L
    private val UNAVAILABLE_STATES = setOf(
        VaultProfileState.UNPROVISIONED,
        VaultProfileState.SETUP_PENDING,
        VaultProfileState.PROFILE_PAUSED,
    )

    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mainService: IHavenService? = null
    private var workService: IHavenService? = null
    private var state: VaultProfileState = VaultProfileState.UNPROVISIONED
    private val activeConsumers = mutableSetOf<String>()

    private var currentHostRef: WeakReference<ComponentActivity>? = null
    private var tryStartLauncher: ActivityResultLauncher<Intent>? = null
    private var bindWorkLauncher: ActivityResultLauncher<Intent>? = null

    private data class PendingRequest(
        val onReady: (IHavenService, IHavenService) -> Unit,
        val onUnavailable: (VaultProfileState) -> Unit,
    )

    private val pendingRequests = mutableListOf<PendingRequest>()
    private val idleDisconnectRunnable = Runnable { if (activeConsumers.isEmpty()) disconnectNow() }

    @JvmStatic
    fun initialize(context: Context) {
        appContext = context.applicationContext
        state = inspectState(appContext)
    }

    private fun currentServices(): Pair<IHavenService, IHavenService>? {
        val m = mainService ?: return null
        val w = workService ?: return null
        return if (isAlive(m) && isAlive(w)) m to w else null
    }

    fun findManagedProfileUser(context: Context = appContext): UserHandle? {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val self = Process.myUserHandle()
        val profiles = runCatching { launcherApps.profiles }.getOrDefault(emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return profiles.firstOrNull { profile ->
                profile != self && runCatching {
                    launcherApps.getLauncherUserInfo(profile)?.userType == UserManager.USER_TYPE_PROFILE_MANAGED
                }.getOrDefault(false)
            }
        }
        return if (Utility.isWorkProfileAvailable(context)) profiles.firstOrNull { it != self } else null
    }

    fun isWorkProfilePaused(context: Context = appContext): Boolean {
        val workUser = findManagedProfileUser(context) ?: return false
        val userManager = context.getSystemService(UserManager::class.java) ?: return false
        return runCatching { userManager.isQuietModeEnabled(workUser) }.getOrDefault(false)
    }

    fun setWorkProfilePaused(context: Context = appContext, paused: Boolean): Boolean {
        val workUser = findManagedProfileUser(context) ?: return false
        val userManager = context.getSystemService(UserManager::class.java) ?: return false
        val changed = runCatching { userManager.requestQuietModeEnabled(paused, workUser) }.getOrDefault(false)
        if (paused) disconnectNow()
        state = inspectState(context)
        return changed
    }

    fun inspectState(context: Context = appContext): VaultProfileState {
        val available = Utility.isWorkProfileAvailable(context)
        return when {
            !available && LocalStorageManager.getInstance().getBoolean(LocalStorageManager.PREF_IS_SETTING_UP) ->
                VaultProfileState.SETUP_PENDING
            !available -> VaultProfileState.UNPROVISIONED
            isWorkProfilePaused(context) -> VaultProfileState.PROFILE_PAUSED
            currentServices() != null -> VaultProfileState.CONNECTED
            state == VaultProfileState.CONNECTING -> VaultProfileState.CONNECTING
            else -> VaultProfileState.IDLE_DISCONNECTED
        }
    }

    fun registerHost(
        activity: ComponentActivity,
        tryStart: ActivityResultLauncher<Intent>,
        bindWork: ActivityResultLauncher<Intent>,
    ) {
        currentHostRef = WeakReference(activity)
        tryStartLauncher = tryStart
        bindWorkLauncher = bindWork
        if (currentServices() != null) registerStartActivityProxies(activity)
    }

    fun unregisterHost(activity: ComponentActivity) {
        if (currentHostRef?.get() === activity) {
            currentHostRef = null
            tryStartLauncher = null
            bindWorkLauncher = null
        }
    }

    fun acquire(
        host: ComponentActivity,
        consumerTag: String = "default",
        onReady: (IHavenService, IHavenService) -> Unit,
        onUnavailable: (VaultProfileState) -> Unit = {},
    ) {
        mainHandler.removeCallbacks(idleDisconnectRunnable)
        activeConsumers.add(consumerTag)
        currentHostRef = WeakReference(host)

        currentServices()?.let { (main, work) ->
            registerStartActivityProxies(host)
            state = VaultProfileState.CONNECTED
            onReady(main, work)
            return
        }

        val inspected = inspectState(host)
        if (inspected in UNAVAILABLE_STATES) {
            state = inspected
            onUnavailable(inspected)
            return
        }

        pendingRequests.add(PendingRequest(onReady, onUnavailable))
        if (state == VaultProfileState.CONNECTING) return

        state = VaultProfileState.CONNECTING
        runCatching { SettingsManager.getInstance().applyAll() }
        (host.application as HavenApplication).bindHavenService(object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                mainService = IHavenService.Stub.asInterface(service)
                launchProfileBridge(host, tryStartLauncher, DummyActivity.TRY_START_SERVICE)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mainService = null
            }
        }, false)
    }

    fun onTryStartResult(result: ActivityResult) {
        val host = currentHostRef?.get()
        if (result.resultCode != Activity.RESULT_OK || host == null || host.isFinishing || host.isDestroyed) {
            failPending(VaultProfileState.PROFILE_PAUSED)
            return
        }
        launchProfileBridge(host, bindWorkLauncher, DummyActivity.START_SERVICE)
    }

    fun onBindWorkResult(result: ActivityResult) {
        val host = currentHostRef?.get()
        val binder = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getBundleExtra("extra")?.getBinder("service")
        } else {
            null
        }
        val work = binder?.let(IHavenService.Stub::asInterface)?.also { workService = it }
        val main = mainService
        if (host == null || main == null || work == null) {
            failPending(VaultProfileState.ERROR)
            return
        }
        registerStartActivityProxies(host)
        state = VaultProfileState.CONNECTED
        drainPending { it.onReady(main, work) }
    }

    fun release(consumerTag: String = "default") {
        activeConsumers.remove(consumerTag)
        if (activeConsumers.isEmpty()) {
            mainHandler.removeCallbacks(idleDisconnectRunnable)
            mainHandler.postDelayed(idleDisconnectRunnable, IDLE_DISCONNECT_DELAY_MS)
        }
    }

    private fun disconnectNow() {
        mainHandler.removeCallbacks(idleDisconnectRunnable)
        val work = workService
        val main = mainService
        workService = null
        mainService = null
        runCatching { work?.stopHavenService(true) }
        runCatching { main?.stopHavenService(false) }
        (appContext as? HavenApplication)?.unbindHavenService()
        state = inspectState(appContext)
    }

    private fun launchProfileBridge(
        host: ComponentActivity,
        launcher: ActivityResultLauncher<Intent>?,
        action: String,
    ) {
        if (launcher == null) {
            failPending(VaultProfileState.ERROR)
            return
        }
        val bridgeIntent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            Utility.transferIntentToProfile(host, bridgeIntent)
            launcher.launch(bridgeIntent)
        } catch (_: IllegalStateException) {
            LocalStorageManager.getInstance().setBoolean(LocalStorageManager.PREF_HAS_SETUP, false)
            failPending(VaultProfileState.UNPROVISIONED)
        } catch (_: Exception) {
            failPending(VaultProfileState.ERROR)
        }
    }

    private fun registerStartActivityProxies(host: ComponentActivity) {
        val hostRef = WeakReference(host)
        try {
            mainService?.setStartActivityProxy(object : IStartActivityProxy.Stub() {
                override fun startActivity(intent: Intent) {
                    val activity = hostRef.get() ?: currentHostRef?.get() ?: return
                    activity.startActivity(intent)
                }
            })
            workService?.setStartActivityProxy(object : IStartActivityProxy.Stub() {
                override fun startActivity(intent: Intent) {
                    val activity = hostRef.get() ?: currentHostRef?.get() ?: return
                    val dummyIntent = Intent(intent.action)
                    Utility.transferIntentToProfileUnsigned(activity, dummyIntent)
                    intent.component = dummyIntent.component
                    activity.startActivity(intent)
                }
            })
        } catch (_: RemoteException) {
        }
    }

    private fun failPending(failureState: VaultProfileState) {
        state = failureState
        drainPending { it.onUnavailable(failureState) }
    }

    private inline fun drainPending(block: (PendingRequest) -> Unit) {
        val snapshot = pendingRequests.toList()
        pendingRequests.clear()
        snapshot.forEach(block)
    }

    private fun isAlive(service: IHavenService): Boolean = runCatching { service.ping() }.isSuccess
}
