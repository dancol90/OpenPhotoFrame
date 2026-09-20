package io.github.micw.openphotoframe

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.DocumentsContract
import android.util.Log
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

// Common file manager apps, tried by package name so they launch as a normal
// app (with delete/rename support) instead of a single-file picker dialog.
private val KNOWN_FILE_MANAGER_PACKAGES = listOf(
    "com.google.android.apps.nbu.files", // Files by Google
    "com.android.documentsui", // AOSP Files
    "com.sec.android.app.myfiles", // Samsung My Files
    "com.mi.android.globalFileexplorer", // Xiaomi/MIUI File Manager
    "com.miui.filemanager",
    "com.huawei.filemanager",
    "com.coloros.filemanager", // Oppo/Realtek
    "com.oppo.filemanager",
    "com.oneplus.filemanager",
    "com.lenovo.FileBrowser",
    "com.asus.filemanager",
    "com.mediatek.filemanager",
)

class FileManagerHandler(private val activity: Activity) {
    fun configureChannel(flutterEngine: FlutterEngine) {
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "io.github.micw.openphotoframe/file_manager"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "openFileManager" -> result.success(openFileManager())
                else -> result.notImplemented()
            }
        }
    }

    private fun openFileManager(): Boolean {
        val intents = mutableListOf(
            Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_FILES"),
        )
        for (packageName in KNOWN_FILE_MANAGER_PACKAGES) {
            activity.packageManager.getLaunchIntentForPackage(packageName)?.let { intents.add(it) }
        }
        intents.add(
            Intent("android.provider.action.BROWSE").setData(
                DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
            )
        )
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.d("FileManagerHandler", "No activity for ${intent.action}", e)
            } catch (e: SecurityException) {
                Log.w("FileManagerHandler", "Cannot launch ${intent.action}", e)
            }
        }
        return false
    }
}
