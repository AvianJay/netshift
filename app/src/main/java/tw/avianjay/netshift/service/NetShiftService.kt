package tw.avianjay.netshift.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tw.avianjay.netshift.appContainer

/** 常駐前景服務：跑自動切換，並把目前狀態顯示在通知上。 */
class NetShiftService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var inForeground = false
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        val c = appContainer
        val notification = Notifications.status(this, c.network.type.value, c.controller.snapshot.value, c.backends.status.value)
        try {
            // specialUse 是 API 34 的常數（編譯期內嵌）；ServiceCompat 會在舊版系統濾掉不認得的 type
            ServiceCompat.startForeground(
                this, Notifications.ID_STATUS, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            inForeground = true
            _running.value = true
        } catch (e: Exception) {
            // Android 12+ 從背景啟動前景服務會被擋（例如被系統以 START_STICKY 重建、又不在電池白名單時）
            Log.w(TAG, "startForeground failed", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            appContainer.settings.update { it.copy(serviceEnabled = false) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!inForeground) return START_NOT_STICKY
        if (!started) {
            started = true
            val c = appContainer
            scope.launch { c.controller.runAutoSwitch() }
            scope.launch {
                val nm = getSystemService(NotificationManager::class.java)
                combine(c.network.type, c.controller.snapshot, c.backends.status, ::Triple)
                    .distinctUntilChanged()
                    .collect { (net, snap, priv) ->
                        nm.notify(Notifications.ID_STATUS, Notifications.status(this@NetShiftService, net, snap, priv))
                    }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        _running.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NetShiftService"
        const val ACTION_STOP = "tw.avianjay.netshift.action.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, NetShiftService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NetShiftService::class.java))
        }
    }
}
