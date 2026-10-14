package tw.avianjay.netshift.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/** 開啟各種系統頁面；找不到對應 Activity 時回傳 false，不 crash。 */
object SystemIntents {

    const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    const val SUI_URL = "https://github.com/RikkaApps/Sui"

    fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, url.toUri()))

    fun developerOptions(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) ||
            start(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** 先試直接跳出「允許在背景執行」對話框，不行再退到白名單列表頁 */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) =
        start(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
        ) || start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun notificationSettings(context: Context) =
        start(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
