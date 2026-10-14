package tw.avianjay.netshift.priv

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ShizukuState {
    /** 沒收到 binder：Shizuku 沒啟動，或沒裝 Sui */
    data object NotRunning : ShizukuState

    /** Shizuku 太舊（API < 11），不支援現行的授權流程 */
    data object Unsupported : ShizukuState

    /** 服務在，但本 App 未授權；[permanentlyDenied] = 使用者選了「拒絕且不再詢問」 */
    data class NoPermission(val permanentlyDenied: Boolean) : ShizukuState

    data class Ready(val uid: Int, val apiVersion: Int, val isSui: Boolean) : ShizukuState {
        val identity: String
            get() = when {
                isSui -> "Sui (root)"
                uid == ROOT_UID -> "Shizuku (root)"
                uid == SHELL_UID -> "Shizuku (adb)"
                else -> "Shizuku (uid $uid)"
            }
    }
}

const val ROOT_UID = 0
const val SHELL_UID = 2000

/**
 * 透過 Shizuku API 執行 shell 指令。Sui 提供的是同一套 API，所以這裡兩者共用，
 * 只靠 [Shizuku.getUid]（0 = root、2000 = adb shell）與 [Sui.isSui] 區分身分。
 *
 * 可用性只看 [Shizuku.pingBinder] + [Shizuku.checkSelfPermission]，
 * 刻意不檢查 Shizuku manager App 是否安裝——Sui 沒有 manager App，查 package 會誤判。
 */
class ShizukuBackend : PrivBackend {

    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val onBinderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val onBinderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPermissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        // sticky：如果 binder 早就送到了（ShizukuProvider 在 Application.onCreate 之前就建立），會立刻回呼一次
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermissionResult)
        refresh()
    }

    override val label: String
        get() = (state.value as? ShizukuState.Ready)?.identity ?: "Sui / Shizuku"

    override fun isAvailable(): Boolean = refresh() is ShizukuState.Ready

    fun refresh(): ShizukuState = query().also { _state.value = it }

    /** @return 是否真的送出了授權請求（服務沒跑、或已被永久拒絕時不會送） */
    fun requestPermission(): Boolean {
        val s = refresh()
        if (s !is ShizukuState.NoPermission || s.permanentlyDenied) return false
        return runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Log.w(TAG, "requestPermission failed", it) }
            .isSuccess
    }

    override suspend fun exec(cmd: String): ExecResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext ExecResult.failure("Sui / Shizuku 未就緒或未授權")
        val process = try {
            newProcess.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        } catch (e: InvocationTargetException) {
            return@withContext ExecResult.failure(e.targetException.toString())
        } catch (e: Exception) {
            return@withContext ExecResult.failure(e.toString())
        }
        process.collect()
    }

    private fun query(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> ShizukuState.NotRunning
            Shizuku.isPreV11() -> ShizukuState.Unsupported
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                ShizukuState.Ready(Shizuku.getUid(), Shizuku.getVersion(), Sui.isSui())
            else -> ShizukuState.NoPermission(Shizuku.shouldShowRequestPermissionRationale())
        }
    } catch (e: Exception) {
        // binder 在查詢途中死掉時，getUid() 等會丟 IllegalStateException / RuntimeException
        Log.w(TAG, "query failed", e)
        ShizukuState.NotRunning
    }

    /** stdout / stderr 必須同時讀，否則其中一邊塞滿 pipe 時子程序會卡住 */
    private suspend fun Process.collect(): ExecResult = coroutineScope {
        runCatching { outputStream.close() }
        val out = async(Dispatchers.IO) { inputStream.bufferedReader().use { it.readText() } }
        val err = async(Dispatchers.IO) { errorStream.bufferedReader().use { it.readText() } }
        val timedOut = AtomicBoolean(false)
        val watchdog = launch {
            delay(EXEC_TIMEOUT_MS)
            timedOut.set(true)
            destroy()
        }
        val code = runInterruptible(Dispatchers.IO) { waitFor() }
        watchdog.cancel()
        val stderr = err.await().trim()
        ExecResult(
            code = code,
            stdout = out.await().trim(),
            stderr = if (timedOut.get()) "逾時（${EXEC_TIMEOUT_MS / 1000} 秒）$stderr" else stderr,
        )
    }

    companion object {
        private const val TAG = "ShizukuBackend"
        private const val REQUEST_CODE = 0x5E7
        private const val EXEC_TIMEOUT_MS = 10_000L

        /**
         * Shizuku API 13 把 newProcess 改成 private static（官方建議改用 UserService），
         * 但一次性的 shell 指令用它最簡單，所以用反射取用。release 開混淆時見 proguard-rules.pro。
         */
        private val newProcess: Method by lazy {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        }
    }
}
