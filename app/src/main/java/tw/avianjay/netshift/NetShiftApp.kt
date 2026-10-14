package tw.avianjay.netshift

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tw.avianjay.netshift.core.NetShiftController
import tw.avianjay.netshift.data.LogRepository
import tw.avianjay.netshift.data.SettingsRepository
import tw.avianjay.netshift.net.NetworkMonitor
import tw.avianjay.netshift.priv.BackendManager
import tw.avianjay.netshift.priv.RootBackend
import tw.avianjay.netshift.priv.ShizukuBackend
import tw.avianjay.netshift.service.Notifications

class NetShiftApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // libsu 的預設 Shell 設定必須在任何 Shell 建立之前設好
        RootBackend.configureShell()
        Notifications.createChannel(this)
        container = AppContainer(this)
    }
}

/** 整個 process 共用的單例；Activity、Service、BroadcastReceiver 都從這裡拿。 */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(app)
    val logs = LogRepository(app, appScope)
    val network = NetworkMonitor(app).apply { start() }
    val shizuku = ShizukuBackend()
    val root = RootBackend()
    val backends = BackendManager(appScope, settings, shizuku, root)
    val controller = NetShiftController(settings, network, backends, logs)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as NetShiftApp).container
