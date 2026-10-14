package tw.avianjay.netshift.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import tw.avianjay.netshift.appContainer
import tw.avianjay.netshift.service.NetShiftService

/**
 * 開機或 App 更新後重新啟動自動切換服務。
 * 這兩個廣播都在「可從背景啟動前景服務」的豁免清單內。
 * Sui 開機就有；Shizuku（非 root）通常要等使用者重新啟動它，服務會先待命、拿到 binder 後再套用。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val s = context.appContainer.settings.current
        // App 更新時只要服務原本開著就接回去；開機則另外看「開機自動啟動」
        val shouldStart = s.serviceEnabled && (action == Intent.ACTION_MY_PACKAGE_REPLACED || s.startOnBoot)
        if (!shouldStart) return

        runCatching { NetShiftService.start(context) }
            .onFailure { Log.w("BootReceiver", "start service failed on $action", it) }
    }
}
