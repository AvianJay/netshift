# NetShift（網速調節器）

依目前網路類型（Wi-Fi / 行動數據）自動切換 Android 的系統下載頻寬限制與 Data Saver。

本 App 不呼叫任何受限 API，而是透過 **Shizuku / Sui** 以 shell（uid 2000）或 root（uid 0）身分執行：

| 功能 | 寫入 | 讀取 |
|---|---|---|
| 下載頻寬限制 | `settings put global ingress_rate_limit_bytes_per_second <B/s>` | `settings get global ingress_rate_limit_bytes_per_second` |
| Data Saver | `cmd netpolicy set restrict-background true\|false` | `cmd netpolicy get restrict-background` |

`-1` = 無限制；128 kbps = 16000、256 kbps = 32000、1 Mbps = 125000、5 Mbps = 625000、15 Mbps = 1875000（單位 bytes/s）。

## 專案結構

```
netshift/
├── settings.gradle.kts            # repositories（含只給 libsu 用的 JitPack）
├── build.gradle.kts               # 根專案，只宣告 plugin 版本
├── gradle.properties
├── gradle/libs.versions.toml      # 所有版本集中在這
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro         # 保留反射用的 Shizuku.newProcess
    └── src/main/
        ├── AndroidManifest.xml    # ShizukuProvider、前景服務、BootReceiver
        ├── res/                   # 圖示、主題、app_name（values-zh-rTW = 網速調節器）
        └── java/tw/avianjay/netshift/
            ├── NetShiftApp.kt             # Application + AppContainer（手動 DI）
            ├── priv/
            │   ├── PrivBackend.kt         # 介面 isAvailable / exec / label、ExecResult、PrivMode
            │   ├── ShizukuBackend.kt      # Shizuku & Sui；pingBinder + checkSelfPermission + getUid
            │   ├── RootBackend.kt         # libsu Shell.cmd()，只在偵測到 su 時可用
            │   └── BackendManager.kt      # 依「權限模式」挑 backend，提供狀態給 UI
            ├── core/
            │   ├── SystemCommands.kt      # 指令字串與輸出解析
            │   ├── Limits.kt              # 預設值、單位換算、格式化
            │   └── NetShiftController.kt  # 規則判斷、寫入、讀回驗證、寫紀錄
            ├── net/NetworkMonitor.kt      # registerDefaultNetworkCallback → WIFI / CELLULAR / NONE
            ├── data/
            │   ├── SettingsRepository.kt  # SharedPreferences
            │   └── LogRepository.kt       # 切換紀錄（JSON 檔，最多 300 筆）
            ├── service/
            │   ├── NetShiftService.kt     # specialUse 前景服務
            │   └── Notifications.kt       # 常駐通知
            ├── receiver/BootReceiver.kt   # BOOT_COMPLETED / MY_PACKAGE_REPLACED
            ├── util/SystemIntents.kt      # 開設定頁、電池白名單
            └── ui/
                ├── MainActivity.kt / MainViewModel.kt / NetShiftRoot.kt
                ├── theme/Theme.kt
                ├── components/Common.kt
                └── screens/  StatusScreen / SettingsScreen / LogScreen / ShizukuGuide / LimitSelector
```

## 建置

工具鏈：AGP 9.1.1（內建 Kotlin）、Kotlin 2.2.10、Compose BOM 2026.06.01、Gradle 9.7.1、compileSdk 36、minSdk 30、JDK 17+。

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### CI 發佈（`.github/workflows/build.yml`）

- push 到 `main` → 更新 [Nightly](https://github.com/AvianJay/netshift/releases/tag/nightly) pre-release，APK 固定叫 `NetShift-nightly.apk`，versionName 帶 `-nightly.<run>+<sha>`。
- push `v*` tag → 建立正式 release；tag 必須等於 `v` + `app/build.gradle.kts` 的 `versionName`，否則建置失敗。

Repository secrets：`KEYSTORE_BASE64`（keystore 檔的 base64）、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`。本機沒設 `KEYSTORE_FILE` 等環境變數時，release 會輸出未簽章的 `app-release-unsigned.apk`。

## 安裝與授權 Shizuku / Sui

App 只透過 Shizuku API 判斷可用性（`pingBinder()` + `checkSelfPermission()`），**不檢查** `moe.shizuku.privileged.api` 是否安裝，所以 Sui 使用者不需要 Shizuku App。

### A. 已 root（Magisk / KernelSU）→ 用 Sui

1. 從 [Sui Releases](https://github.com/RikkaApps/Sui/releases) 下載 zip，在 Magisk →「模組」→「從本機安裝」，重新開機。
2. 開啟 NetShift →「狀態」頁應顯示「Sui / Shizuku：未授權」→ 按「請求授權」→ Sui 跳窗選「允許」。
3. 權限卡會顯示 **生效中：Sui (root)**、身分 **uid 0（root）**。
4. 若之前按了「拒絕且不再詢問」：在撥號程式輸入 `*#*#784784#*#*` 開啟 Sui 管理介面，把 NetShift 改成允許。

Sui 在開機時就會啟動，所以開機自動啟動的服務可以直接套用規則。

### B. 未 root → 用 Shizuku（無線偵錯，Android 11+）

1. 安裝 Shizuku：<https://shizuku.rikka.app/download/>（或 Google Play）。
2. 設定 →「關於手機」連點「版本號碼」7 次開啟開發人員選項。
3. 連上 Wi-Fi，開發人員選項 → 開啟「**無線偵錯**」。
4. Shizuku →「透過無線偵錯啟動」→「配對」，接著在無線偵錯頁點「使用配對碼配對裝置」，把配對碼輸入 Shizuku 的通知。
5. 配對完成後在 Shizuku 按「**啟動**」，首頁顯示「Shizuku 正在執行」。
6. 開啟 NetShift → 按「請求授權」→ 選「允許」。權限卡會顯示 **Shizuku (adb)**、**uid 2000（adb shell）**。
7. 若之前拒絕過：Shizuku →「已授權的應用程式」→ 打開 NetShift。

> 非 root 的 Shizuku 在**每次重新開機後都要再按一次「啟動」**（部分新版 Shizuku 有開機自動啟動的選項，請以 App 內設定為準）。NetShift 的服務開機後會先待命，拿到 Shizuku binder 後自動套用。

也可以用電腦啟動：USB 偵錯連上電腦，執行 Shizuku App「透過連接電腦啟動」頁面上顯示的 `adb shell ...` 指令。

### C. 用 rish 手動驗證（選用）

rish 是 Shizuku 附的 shell，可以在 Termux 等終端機 App 以同樣的 shell 身分執行指令（Shizuku →「在終端機應用程式中使用 Shizuku」匯出 `rish` 與 `rish_shizuku.dex`）。先確認系統接受這些指令：

```sh
rish -c 'id'                                                    # uid=2000(shell) 或 uid=0(root)
rish -c 'settings get global ingress_rate_limit_bytes_per_second'
rish -c 'settings put global ingress_rate_limit_bytes_per_second 125000'
rish -c 'cmd netpolicy get restrict-background'
rish -c 'settings put global ingress_rate_limit_bytes_per_second -1' # 還原
```

注意：rish 本身也需要 Shizuku 授權給終端機 App；**NetShift 的授權仍然要透過上面的「請求授權」或 Shizuku 的授權清單完成**，rish 無法代替。

## 權限模式

設定頁 →「權限模式」：

- **自動**（預設）：使用 Sui / Shizuku。另有「Sui / Shizuku 不可用時改用 Root」開關，**預設關閉**，打開時會先請求一次 root。
- **Sui / Shizuku**：只用 Shizuku API。
- **Root**：用 libsu 的 `Shell.cmd()`；只在 PATH 中找得到 `su` 時可用，選擇時會跳出 Magisk 授權視窗。

## 切換規則

- 連上 Wi-Fi：套用 Wi-Fi 規則（預設 -1 無限制）。
- 使用行動數據：套用行動數據規則（預設 125000 = 1 Mbps），可選擇同時開 Data Saver。
- 離開行動數據時，只關掉**本 App 自己開的** Data Saver；使用者手動開的不會動。
- 規則停用時不改動該網路；但若當下的限制是本 App 設的，會還原為 -1。
- 無網路時不做任何事，等下一個網路出現（切換時會等 1 秒讓網路穩定）。
- 每次寫入後都會讀回驗證，結果記錄在「紀錄」頁。

## 已知限制

- `ingress_rate_limit_bytes_per_second` 是 **Android 13** 加入的；Android 11/12 寫得進去但不會生效（App 會顯示提示）。實際限速由 ConnectivityService 透過 eBPF 套用到網路介面，部分廠商 ROM / 舊核心可能不支援。
- 系統不會對 VPN 介面限速，但會限制 VPN 底下實際的 Wi-Fi / 行動網路介面。
- 停止自動切換服務時不會還原目前的限制值；需要的話先把兩個規則設成「無限制」再按「立即套用規則」。
