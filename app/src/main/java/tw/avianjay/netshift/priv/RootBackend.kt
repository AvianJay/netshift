package tw.avianjay.netshift.priv

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class RootState(val description: String) {
    NOT_FOUND("未偵測到 root"),
    UNKNOWN("偵測到 su，尚未請求"),
    GRANTED("已授權"),
    DENIED("已拒絕或逾時"),
}

/**
 * 用 libsu 的 su shell 執行指令。只在偵測到 su 時才可能啟用，
 * 而且只有使用者明確選擇 Root 模式（或在自動模式下打開「退回 root」）才會去要權限。
 */
class RootBackend : PrivBackend {

    private val _state = MutableStateFlow(computeState())
    val state: StateFlow<RootState> = _state.asStateFlow()

    override val label: String = "Root (libsu)"

    override fun isAvailable(): Boolean = Shell.getCachedShell()?.let { it.isAlive && it.isRoot } == true

    /**
     * 已經有 root shell 就直接用；沒有就開一個。Magisk 已記住授權時不會跳窗，
     * 第一次則會跳出 Magisk 的授權視窗（最多等 [SU_TIMEOUT_SECONDS] 秒）。
     * 先前已經拿到非 root shell（被拒絕）時不會重問，要重問請用 [request]。
     */
    suspend fun ensure(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable() && Shell.getCachedShell() == null && suExists()) {
            runCatching { Shell.getShell() }
        }
        updateState()
    }

    /** 使用者主動按「請求 Root」：丟掉先前的非 root shell，重新要一次。 */
    suspend fun request(): Boolean = withContext(Dispatchers.IO) {
        if (!suExists()) return@withContext updateState()
        Shell.getCachedShell()?.takeIf { !it.isRoot }?.close()
        runCatching { Shell.getShell() }
        updateState()
    }

    override suspend fun exec(cmd: String): ExecResult = withContext(Dispatchers.IO) {
        // libsu 拿不到 su 時會退回一般 sh；那種 shell 執行的指令沒有權限，不能用
        if (!isAvailable()) return@withContext ExecResult.failure("沒有 root shell")
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val result = Shell.cmd(cmd).to(out, err).exec()
        ExecResult(result.code, out.joinToString("\n").trim(), err.joinToString("\n").trim())
    }

    private fun updateState(): Boolean {
        _state.value = computeState()
        return _state.value == RootState.GRANTED
    }

    private fun computeState(): RootState {
        val shell = Shell.getCachedShell()
        return when {
            shell != null && shell.isAlive && shell.isRoot -> RootState.GRANTED
            !suExists() -> RootState.NOT_FOUND
            shell != null -> RootState.DENIED
            else -> RootState.UNKNOWN
        }
    }

    /** 與 libsu 內部相同的偵測方式：PATH 裡有可執行的 su */
    private fun suExists(): Boolean =
        System.getenv("PATH").orEmpty().split(':').any { File(it, "su").canExecute() }

    companion object {
        private const val SU_TIMEOUT_SECONDS = 15L

        /** 必須在任何 Shell 建立之前呼叫（放在 Application.onCreate） */
        fun configureShell() {
            Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(SU_TIMEOUT_SECONDS))
        }
    }
}
