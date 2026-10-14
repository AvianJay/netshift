package tw.avianjay.netshift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tw.avianjay.netshift.priv.ShizukuState
import tw.avianjay.netshift.ui.components.SectionCard
import tw.avianjay.netshift.util.SystemIntents

private const val RISH_EXAMPLE = "rish -c 'settings get global ingress_rate_limit_bytes_per_second'"

/** Sui / Shizuku 尚未就緒時的引導。只看 Shizuku API 回報的狀態，不檢查任何 App 是否安裝。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShizukuGuideCard(
    state: ShizukuState,
    onRequestPermission: () -> Unit,
    onRecheck: () -> Unit,
) {
    val context = LocalContext.current
    SectionCard(title = "設定 Sui / Shizuku") {
        when (state) {
            ShizukuState.NotRunning -> {
                Text("未偵測到 Sui / Shizuku 服務。請擇一完成：", style = MaterialTheme.typography.bodyMedium)
                Step("1", "已 root（Magisk / KernelSU）：安裝 Sui 模組並重新開機。Sui 沒有獨立 App，裝好後回到這裡即可。")
                Step("2", "未 root：安裝 Shizuku → 開發人員選項開啟「無線偵錯」→ 在 Shizuku 用「配對」輸入配對碼 → 按「啟動」。每次重新開機後要再啟動一次。")
                Step("3", "也可以用電腦執行 Shizuku App 內「透過連接電腦啟動」顯示的 adb 指令。")
            }
            ShizukuState.Unsupported ->
                Text("Shizuku 版本過舊（API < 11），請更新到最新版。", style = MaterialTheme.typography.bodyMedium)
            is ShizukuState.NoPermission -> {
                if (state.permanentlyDenied) {
                    Text(
                        "先前已拒絕且勾選「不再詢問」。請手動允許：\n" +
                            "・Shizuku：開啟 Shizuku App →「已授權的應用程式」→ 打開 NetShift\n" +
                            "・Sui：在撥號程式輸入 *#*#784784#*#* 開啟 Sui 管理介面 → 允許 NetShift",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("服務已執行，但尚未授權本 App。按下方按鈕後在跳出的視窗選「允許」。", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRequestPermission) { Text("請求授權") }
                }
            }
            is ShizukuState.Ready -> Text("已就緒：${state.identity}", style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "進階：rish 是 Shizuku 附的終端機 shell，可在 Termux 等終端機 App 先手動驗證 shell 權限能否讀寫這個設定：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                RISH_EXAMPLE,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { SystemIntents.openUrl(context, SystemIntents.SHIZUKU_DOWNLOAD_URL) }) {
                Text("下載 Shizuku")
            }
            OutlinedButton(onClick = { SystemIntents.openUrl(context, SystemIntents.SUI_URL) }) {
                Text("Sui 說明")
            }
            OutlinedButton(onClick = { SystemIntents.developerOptions(context) }) {
                Text("開發人員選項")
            }
            TextButton(onClick = onRecheck) { Text("重新檢查") }
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Text("$number. $text", style = MaterialTheme.typography.bodyMedium)
}
