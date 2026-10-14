package tw.avianjay.netshift.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 下載限制的單位是 bytes/s；1 kbps = 1000 bit/s = 125 B/s */
object Limits {
    const val UNLIMITED = -1L
    const val BYTES_PER_KBPS = 125L

    /** 無限制、128 kbps、256 kbps、1 Mbps、5 Mbps、15 Mbps */
    val PRESETS = listOf(UNLIMITED, 16_000L, 32_000L, 125_000L, 625_000L, 1_875_000L)

    fun fromKbps(kbps: Long): Long = kbps * BYTES_PER_KBPS

    fun format(bytesPerSecond: Long?): String = when {
        bytesPerSecond == null -> "未知"
        bytesPerSecond < 0 -> "無限制"
        else -> {
            val bits = bytesPerSecond * 8
            if (bits >= 1_000_000) "${trim(bits / 1_000_000.0)} Mbps" else "${trim(bits / 1_000.0)} kbps"
        }
    }

    private fun trim(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}

fun formatDataSaver(on: Boolean?): String = when (on) {
    true -> "開啟"
    false -> "關閉"
    null -> "未知"
}

fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
