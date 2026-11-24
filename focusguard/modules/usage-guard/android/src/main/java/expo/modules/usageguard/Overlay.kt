package expo.modules.usageguard

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import java.io.File

/** Full-screen "draw over other apps" alert. Everything it shows/plays comes from local files. */
object Overlay {
  private var view: View? = null
  private var player: MediaPlayer? = null
  fun isShowing() = view != null

  private fun loadBitmap(path: String): Bitmap? {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, b)
    var s = 1
    while (b.outWidth / s > 2048 || b.outHeight / s > 2048) s *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
  }

  fun show(ctx: Context, cfg: JSONObject, pkg: String) {
    Handler(Looper.getMainLooper()).post {
      if (view != null) return@post
      val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val d = ctx.resources.displayMetrics.density
      val root = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }

      val img = cfg.optString("imagePath")
      if (img.isNotEmpty() && File(img).exists()) {
        root.addView(ImageView(ctx).apply {
          scaleType = ImageView.ScaleType.CENTER_CROP
          setImageBitmap(loadBitmap(img))
        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
      }
      root.addView(View(ctx).apply { setBackgroundColor(0x77000000) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

      val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding((32 * d).toInt(), 0, (32 * d).toInt(), 0)
      }
      col.addView(TextView(ctx).apply {
        text = cfg.optString("message", "Time's up!")
        setTextColor(Color.WHITE); textSize = 28f; gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD); setShadowLayer(8f, 0f, 2f, Color.BLACK)
      }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
      col.addView(Button(ctx).apply {
        text = "Dismiss & Close App"; isAllCaps = false; textSize = 18f
        setTextColor(Color.WHITE); setBackgroundColor(0xFFE53935.toInt())
        setOnClickListener { dismiss(ctx, pkg) }
      }, LinearLayout.LayoutParams(MATCH_PARENT, (56 * d).toInt()).apply { topMargin = (40 * d).toInt() })
      root.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))

      val lp = WindowManager.LayoutParams(
        MATCH_PARENT, MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
      )
      try { wm.addView(root, lp); view = root; startAudio(ctx, cfg.optString("audioPath")) } catch (_: Exception) {}
    }
  }

  private fun startAudio(ctx: Context, path: String) {
    try {
      player = MediaPlayer().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        if (path.isNotEmpty() && File(path).exists()) setDataSource(path)
        else setDataSource(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        isLooping = true
        prepare(); start()
      }
    } catch (_: Exception) { player?.release(); player = null }
  }

  private fun dismiss(ctx: Context, pkg: String) {
    try { player?.stop() } catch (_: Exception) {}
    player?.release(); player = null
    view?.let { try { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {} }
    view = null
    if (pkg.isEmpty()) return // test mode
    // Android doesn't let apps force-stop others: send user Home, then kill the now-backgrounded process.
    ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    Handler(Looper.getMainLooper()).postDelayed({
      try { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg) } catch (_: Exception) {}
    }, 600)
  }
}
