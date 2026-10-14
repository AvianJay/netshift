package tw.avianjay.netshift.core

/** 透過 shell 身分執行的系統指令與輸出解析 */
object SystemCommands {

    private const val INGRESS_KEY = "ingress_rate_limit_bytes_per_second"

    /** 下載（ingress）頻寬限制，Android 13 起由 ConnectivityService 讀取並套用到網路介面 */
    const val GET_INGRESS_LIMIT = "settings get global $INGRESS_KEY"

    fun setIngressLimit(bytesPerSecond: Long) = "settings put global $INGRESS_KEY $bytesPerSecond"

    const val GET_DATA_SAVER = "cmd netpolicy get restrict-background"

    fun setDataSaver(on: Boolean) = "cmd netpolicy set restrict-background $on"

    /** 加入 deviceidle 白名單，等同使用者在設定裡把 App 設成「不最佳化」 */
    fun whitelistDeviceIdle(packageName: String) = "cmd deviceidle whitelist +$packageName"

    /** 從未設定過時 `settings get` 會印出 "null"，等同 -1（無限制） */
    fun parseIngressLimit(stdout: String): Long? {
        val v = stdout.trim()
        return if (v.isEmpty() || v == "null") Limits.UNLIMITED else v.toLongOrNull()
    }

    /** 輸出格式：`Restrict background status: enabled` / `disabled` */
    fun parseDataSaver(stdout: String): Boolean? =
        when (stdout.substringAfter(':').trim()) {
            "enabled" -> true
            "disabled" -> false
            else -> null
        }
}
