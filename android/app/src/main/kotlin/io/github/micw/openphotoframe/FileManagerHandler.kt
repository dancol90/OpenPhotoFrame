package io.github.micw.openphotoframe

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.DocumentsContract
import android.util.Log
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

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
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_FILES"),
            Intent("android.provider.action.BROWSE").setData(
                DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
            ),
            // Devices without a standalone file manager can still browse documents.
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
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
