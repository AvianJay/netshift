package tw.avianjay.netshift.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import tw.avianjay.netshift.R
import tw.avianjay.netshift.core.Limits
import tw.avianjay.netshift.core.NetShiftController
import tw.avianjay.netshift.core.formatDataSaver
import tw.avianjay.netshift.net.NetType
import tw.avianjay.netshift.priv.PrivStatus
import tw.avianjay.netshift.ui.MainActivity

object Notifications {
    const val CHANNEL_STATUS = "status"
    const val ID_STATUS = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_STATUS, "常駐狀態", NotificationManager.IMPORTANCE_LOW).apply {
            description = "顯示目前網路類型、下載限制與 Data Saver 狀態"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun status(
        context: Context,
        net: NetType,
        snapshot: NetShiftController.Snapshot,
        priv: PrivStatus,
    ): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, NetShiftService::class.java).setAction(NetShiftService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val backend = priv.active?.label ?: "權限未就緒"
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_netshift)
            .setContentTitle("${net.label}・下載 ${Limits.format(snapshot.limit)}")
            .setContentText("Data Saver ${formatDataSaver(snapshot.dataSaver)}・$backend")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "停止", stop)
            .build()
    }
}
