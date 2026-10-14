package tw.avianjay.netshift.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.avianjay.netshift.R
import tw.avianjay.netshift.ui.screens.LogScreen
import tw.avianjay.netshift.ui.screens.SettingsScreen
import tw.avianjay.netshift.ui.screens.StatusScreen

private enum class Tab(val title: String, @param:DrawableRes val icon: Int) {
    STATUS("狀態", R.drawable.ic_tab_status),
    SETTINGS("設定", R.drawable.ic_tab_settings),
    LOG("紀錄", R.drawable.ic_tab_log),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetShiftRoot(vm: MainViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.STATUS) }
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    AskNotificationPermissionOnce(onResult = vm::onResume)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.STATUS -> StatusScreen(state, device, vm)
                Tab.SETTINGS -> SettingsScreen(state, device, vm)
                Tab.LOG -> LogScreen(logs, onClear = vm::clearLogs)
            }
        }
    }
}

/** Android 13+ 前景服務的通知需要 POST_NOTIFICATIONS；第一次開啟時問一次 */
@Composable
private fun AskNotificationPermissionOnce(onResult: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
