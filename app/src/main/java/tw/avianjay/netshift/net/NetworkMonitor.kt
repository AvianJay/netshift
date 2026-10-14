package tw.avianjay.netshift.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetType(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR("行動數據"),
    /** 乙太網路、藍牙網路共享等，沒有對應規則 */
    OTHER("其他網路"),
    NONE("無網路"),
}

/** 監聽系統「預設網路」的類型。整個 process 只註冊一次，常駐到 process 結束。 */
class NetworkMonitor(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _type = MutableStateFlow(currentType())
    val type: StateFlow<NetType> = _type.asStateFlow()

    private var registered = false

    // 回呼都在 ConnectivityThread 上依序執行，不需要額外同步
    private var defaultNetwork: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            defaultNetwork = network
            _type.value = caps.toNetType()
        }

        override fun onLost(network: Network) {
            // 切換網路時新的預設網路會直接 onAvailable，只有「完全沒網路」才會走到這裡
            if (network == defaultNetwork) {
                defaultNetwork = null
                _type.value = NetType.NONE
            }
        }
    }

    @Synchronized
    fun start() {
        if (registered) return
        cm.registerDefaultNetworkCallback(callback)
        registered = true
    }

    private fun currentType(): NetType {
        val network = cm.activeNetwork ?: return NetType.NONE
        return cm.getNetworkCapabilities(network)?.toNetType() ?: NetType.NONE
    }

    /** VPN 的 capabilities 會帶上底層網路的 transport，所以掛 VPN 時一樣判斷得出 Wi-Fi / 行動數據 */
    private fun NetworkCapabilities.toNetType(): NetType = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.CELLULAR
        else -> NetType.OTHER
    }
}
