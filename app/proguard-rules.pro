# Shizuku API 13 把 newProcess 改成 private，ShizukuBackend 用反射呼叫它
-keepclassmembers class rikka.shizuku.Shizuku {
    private static ** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
