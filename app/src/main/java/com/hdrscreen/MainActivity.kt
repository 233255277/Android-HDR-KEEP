package com.hdrscreen

import android.app.Activity
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.SurfaceView
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var surfaceView: SurfaceView
    private var mediaPlayer: MediaPlayer? = null
    private var overlayButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HdrHelper.setActivityHdrMode(this, true)

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }

        // 全屏SurfaceView用于HDR视频解码
        surfaceView = SurfaceView(this).apply {
            alpha = 1.0f
            holder.setFormat(PixelFormat.RGBA_8888)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP)
        }
        root.addView(surfaceView)

        // 状态标签
        val label = TextView(this).apply {
            text = "方案A: HDR视频解码"
            textSize = 14f; setTextColor(0xAAFFFFFF.toInt())
            setBackgroundColor(0x44000000.toInt()); setPadding(16, 8, 16, 8)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
            lp.setMargins(32, 64, 0, 0); layoutParams = lp
        }
        root.addView(label)

        // 底部按钮栏
        val btnBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1E1E1E.toInt()); setPadding(16, 12, 16, 12)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            lp.setMargins(0, 0, 0, 48); layoutParams = lp
        }

        overlayButton = Button(this).apply {
            text = if (HdrOverlayService.isServiceRunning) "停止悬浮窗" else "启动悬浮窗"
            textSize = 14f
            setBackgroundColor(if (HdrOverlayService.isServiceRunning) 0xFFE53935.toInt() else 0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt()); setPadding(24, 12, 24, 12)
            setOnClickListener { toggleOverlay() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 80); lp.setMargins(0, 0, 12, 0)
            layoutParams = lp
        }
        btnBar.addView(overlayButton!!)

        // 长期后台任务权限设置按钮
        btnBar.addView(Button(this).apply {
            text = "长期后台任务权限设置"
            textSize = 14f; setBackgroundColor(0xFF607D8B.toInt())
            setTextColor(0xFFFFFFFF.toInt()); setPadding(24, 12, 24, 12)
            setOnClickListener {
                try {
                    val intent = Intent()
                    intent.setClassName("com.android.settings",
                        "com.android.settings.Settings\$LongBackgroundTasksActivity")
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "无法打开: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 80); lp.setMargins(0, 0, 12, 0)
            layoutParams = lp
        })
        root.addView(btnBar)
        setContentView(root)
        setupImmersiveMode()

        // Surface准备好后启动视频
        surfaceView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                Log.i("HdrMain", "Surface created: ${holder.surface.isValid}")
                startHdrVideo(holder.surface)
            }
            override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, ht: Int) {
                Log.i("HdrMain", "Surface changed: ${w}x${ht}")
            }
            override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
                Log.i("HdrMain", "Surface destroyed")
                stopHdrVideo()
            }
        })
    }

    private fun startHdrVideo(surface: android.view.Surface) {
        if (mediaPlayer != null) return
        try {
            mediaPlayer = MediaPlayer().apply {
                val uri = Uri.parse("android.resource://${packageName}/${R.raw.hdr_test}")
                setDataSource(this@MainActivity, uri)
                setSurface(surface)
                setLooping(true)
                setVolume(0f, 0f)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.e("HdrMain", "MediaPlayer error: what=$what extra=$extra"); false
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        Log.i("HdrMain", "HDR video rendering started")
                    }; false
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("HdrMain", "Failed to start HDR video: ${e.message}", e)
        }
    }

    private fun stopHdrVideo() {
        try { mediaPlayer?.stop() } catch (e: Exception) {}
        try { mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
    }

    private fun toggleOverlay() {
        try {
            if (HdrOverlayService.isServiceRunning) {
                stopService(Intent(this, HdrOverlayService::class.java))
                HdrOverlayService.setRunning(false)
                overlayButton?.text = "启动悬浮窗"
                overlayButton?.setBackgroundColor(0xFF4CAF50.toInt())
            } else {
                startForegroundService(Intent(this, HdrOverlayService::class.java))
                HdrOverlayService.setRunning(true)
                overlayButton?.text = "停止悬浮窗"
                overlayButton?.setBackgroundColor(0xFFE53935.toInt())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "操作失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION") HdrHelper.setActivityHdrMode(this, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHdrVideo()
    }

    override fun onBackPressed() {
        HdrHelper.setActivityHdrMode(this, false)
        super.onBackPressed()
    }
}