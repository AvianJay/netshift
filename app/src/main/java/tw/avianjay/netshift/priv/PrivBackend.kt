package tw.avianjay.netshift.priv

/** 能以特權身分執行 shell 指令的來源（Shizuku / Sui / root）。 */
interface PrivBackend {
    /** 顯示給使用者看的名稱，例如「Sui (root)」、「Shizuku (adb)」、「Root (libsu)」 */
    val label: String

    /** 不會跳出任何授權視窗的即時檢查 */
    fun isAvailable(): Boolean

    suspend fun exec(cmd: String): ExecResult
}

data class ExecResult(val code: Int, val stdout: String, val stderr: String) {
    /** `settings` / `cmd` 失敗時多半是 exit code 非 0，但有些 ROM 只在 stderr 印 Exception */
    val ok: Boolean get() = code == 0 && "Exception" !in stderr

    /** 失敗時給 log 用的一行摘要 */
    val message: String
        get() = stderr.ifBlank { stdout }
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(200)
            ?: "exit code $code"

    companion object {
        fun failure(reason: String) = ExecResult(-1, "", reason)
    }
}

enum class PrivMode(val title: String, val description: String) {
    AUTO("自動", "優先使用 Sui / Shizuku；可選擇在不可用時退回 root"),
    SHIZUKU("Sui / Shizuku", "只透過 Shizuku API 執行（Sui 與 Shizuku App 皆可）"),
    ROOT("Root", "透過 libsu 執行 su，只在偵測到 root 時可用"),
}
