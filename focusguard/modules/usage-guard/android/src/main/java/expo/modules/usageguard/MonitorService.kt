package expo.modules.usageguard

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/** Foreground service: polls UsageStatsManager events each second and accumulates today's foreground time per monitored app. */
class MonitorService : Service() {
  private val h = Handler(Looper.getMainLooper())
  private lateinit var prefs: SharedPreferences
  private var cur: String? = null
  private var lastEv = 0L
  private var lastTick = 0L

  private val loop = object : Runnable {
    override fun run() { try { step() } catch (_: Exception) {}; h.postDelayed(this, 1000) }
  }

  override fun onCreate() {
    prefs = getSharedPreferences("ug", Context.MODE_PRIVATE)
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("ug", "App monitoring", NotificationManager.IMPORTANCE_LOW))
    val n = Notification.Builder(this, "ug").setContentTitle("FocusGuard is monitoring")
      .setContentText("Tracking time in selected apps (on-device only)")
      .setSmallIcon(applicationInfo.icon).setOngoing(true).build()
    if (Build.VERSION.SDK_INT >= 34)
      ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    else startForeground(1, n)
    val now = System.currentTimeMillis()
    lastEv = now - 2 * 3600_000L // replay recent events to find the current foreground app
    lastTick = now
    h.post(loop)
  }

  override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
  override fun onBind(i: Intent?): IBinder? = null
  override fun onDestroy() { h.removeCallbacks(loop) }

  private fun step() {
    val now = System.currentTimeMillis()
    val dt = now - lastTick; lastTick = now
    val cfg = JSONObject(prefs.getString("config", "{}")!!)
    val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val ev = usm.queryEvents(lastEv, now); lastEv = now
    val e = UsageEvents.Event()
    while (ev.hasNextEvent()) {
      ev.getNextEvent(e)
      when (e.eventType) {
        1 -> cur = e.packageName                       // ACTIVITY_RESUMED
        2 -> if (e.packageName == cur) cur = null      // ACTIVITY_PAUSED
        16 -> cur = null                               // SCREEN_NON_INTERACTIVE
      }
    }
    val pkg = cur ?: return
    val pkgs = cfg.optJSONArray("packages") ?: return
    if ((0 until pkgs.length()).none { pkgs.getString(it) == pkg }) return

    // daily usage store, resets at midnight
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    var u = JSONObject(prefs.getString("usage", "{}")!!)
    if (u.optString("day") != today) u = JSONObject().put("day", today).put("apps", JSONObject())
    val apps = u.getJSONObject("apps")
    val total = apps.optLong(pkg, 0) + dt
    apps.put(pkg, total)
    prefs.edit().putString("usage", u.toString()).apply()

    val limitMs = cfg.optInt("limitMin", 15) * 60_000L
    if (total >= limitMs && !Overlay.isShowing()) {
      val log = JSONArray(prefs.getString("log", "[]")!!)
      log.put(JSONObject().put("t", now).put("pkg", pkg).put("ms", total))
      val trimmed = JSONArray(); for (i in maxOf(0, log.length() - 200) until log.length()) trimmed.put(log.get(i))
      prefs.edit().putString("log", trimmed.toString()).apply()
      Overlay.show(applicationContext, cfg, pkg)
    }
  }
}
