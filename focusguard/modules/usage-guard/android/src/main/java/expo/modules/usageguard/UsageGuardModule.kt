package expo.modules.usageguard

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class UsageGuardModule : Module() {
  private val ctx: Context get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()
  private val prefs get() = ctx.getSharedPreferences("ug", Context.MODE_PRIVATE)

  private fun usageOk(): Boolean {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
  }

  private fun launch(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

  private fun sync(json: String) {
    val svc = Intent(ctx, MonitorService::class.java)
    if (JSONObject(json).optBoolean("enabled") && usageOk() && Settings.canDrawOverlays(ctx))
      ContextCompat.startForegroundService(ctx, svc)
    else ctx.stopService(svc)
  }

  override fun definition() = ModuleDefinition {
    Name("UsageGuard")

    Function("hasUsagePermission") { usageOk() }
    Function("hasOverlayPermission") { Settings.canDrawOverlays(ctx) }
    Function("openUsageSettings") { launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    Function("openOverlaySettings") {
      launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
    }

    AsyncFunction("getInstalledApps") {
      val pm = ctx.packageManager
      val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      pm.queryIntentActivities(q, 0).map { it.activityInfo.packageName }.distinct()
        .filter { it != ctx.packageName }
        .map { p ->
          val bmp = pm.getApplicationIcon(p).toBitmap(64, 64)
          val out = ByteArrayOutputStream()
          bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
          mapOf(
            "packageName" to p,
            "label" to pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString(),
            "icon" to Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
          )
        }.sortedBy { (it["label"] as String).lowercase() }
    }

    Function("getConfig") { prefs.getString("config", "{}") ?: "{}" }
    Function("saveConfig") { json: String ->
      prefs.edit().putString("config", json).apply()
      sync(json)
    }

    AsyncFunction("copyToInternal") { uri: String, name: String ->
      val dest = File(ctx.filesDir, name)
      ctx.contentResolver.openInputStream(Uri.parse(uri))!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
      dest.absolutePath
    }

    Function("testOverlay") {
      Overlay.show(ctx.applicationContext, JSONObject(prefs.getString("config", "{}")!!), "")
    }

    Function("getStats") {
      JSONObject()
        .put("usage", JSONObject(prefs.getString("usage", "{}")!!))
        .put("log", JSONArray(prefs.getString("log", "[]")!!)).toString()
    }
    Function("clearStats") { prefs.edit().remove("usage").remove("log").apply() }
  }
}
