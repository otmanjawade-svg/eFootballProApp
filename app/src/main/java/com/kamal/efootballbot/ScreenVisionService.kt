package com.kamal.efootballbot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI

class ScreenVisionService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var visionHandler: Handler

    private var windowManager: WindowManager? = null
    private var floatingBtn: Button? = null
    private var isBotPlaying = false
    private var lastActionTime = 0L
    private var screenW = 2400f
    private var screenH = 1080f

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("VisionThread").apply { start() }
        visionHandler = Handler(thread.looper)

        val metrics = resources.displayMetrics
        screenW = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        screenH = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()

        startForegroundNotification()
        setupFloatingOverlay()
    }

    private fun setupFloatingOverlay() {
        Handler(Looper.getMainLooper()).post {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingBtn = Button(this).apply {
                text = "▶ تشغيل البوت"
                setBackgroundColor(Color.parseColor("#CC1B5E20"))
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(24, 12, 24, 12)
                setOnClickListener {
                    isBotPlaying = !isBotPlaying
                    if (isBotPlaying) {
                        text = "⏸ إيقاف البوت"
                        setBackgroundColor(Color.parseColor("#CCB71C1C"))
                    } else {
                        text = "▶ تشغيل البوت"
                        setBackgroundColor(Color.parseColor("#CC1B5E20"))
                        BotAccessibilityService.instance?.releaseJoystick()
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 25
            }
            windowManager?.addView(floatingBtn, params)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("DATA") ?: return START_NOT_STICKY

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)

        val width = 640
        val height = 360
        val density = resources.displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "eFootballVision", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, visionHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (isBotPlaying) {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    analyzeAndPlay(bitmap, width, height)
                    bitmap.recycle()
                }
            } finally {
                image.close()
            }
        }, visionHandler)

        return START_STICKY
    }

    private fun analyzeAndPlay(frame: Bitmap, w: Int, h: Int) {
        val bot = BotAccessibilityService.instance ?: return
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 160) return
        lastActionTime = now

        // 1. البحث عن الكرة البيضاء الساطعة ومؤشر اللاعب في وسط الملعب
        var ballX = w / 2f
        var ballY = h / 2f
        var foundBall = false

        var playerX = w / 2f
        var playerY = h / 2f
        var foundCursor = false

        for (y in (h * 0.14).toInt() until (h * 0.80).toInt() step 3) {
            for (x in (w * 0.06).toInt() until (w * 0.94).toInt() step 3) {
                val pixel = frame.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // مؤشر اللاعب السماوي أو الأحمر الفاتح
                if (!foundCursor && b > 210 && g > 190 && r < 110) {
                    playerX = x.toFloat()
                    playerY = (y + 22).toFloat()
                    foundCursor = true
                }
                // الكرة البيضاء
                if (!foundBall && r > 235 && g > 235 && b > 235) {
                    ballX = x.toFloat()
                    ballY = y.toFloat()
                    foundBall = true
                }
            }
        }

        // 2. إحداثيات الأزرار النسبية لشاشة سامسونج بالعرض
        val joyX = screenW * 0.14f
        val joyY = screenH * 0.72f
        val passX = screenW * 0.82f
        val passY = screenH * 0.80f
        val throughX = screenW * 0.86f
        val throughY = screenH * 0.62f
        val shootX = screenW * 0.92f
        val shootY = screenH * 0.75f
        val dashX = screenW * 0.92f
        val dashY = screenH * 0.50f

        val distToBall = hypot(playerX - ballX, playerY - ballY) / w
        val hasBall = distToBall < 0.085f
        val progress = playerX / w

        if (hasBall) {
            val goalAngle = atan2((h * 0.50f - playerY).toDouble(), (w * 0.95f - playerX).toDouble())
            when {
                progress > 0.74f -> {
                    bot.steerJoystick(joyX, joyY, goalAngle, 1.0f)
                    bot.finesseShot(shootX, shootY)
                }
                progress > 0.42f -> {
                    bot.steerJoystick(joyX, joyY, goalAngle, 0.80f)
                    if ((now / 1000) % 3 == 0L) {
                        bot.throughBall(throughX, throughY)
                    } else {
                        bot.shortPass(passX, passY)
                    }
                }
                else -> {
                    bot.steerJoystick(joyX, joyY, goalAngle, 0.70f)
                    bot.shortPass(passX, passY)
                }
            }
        } else {
            // الدفاع: الرجوع لتغطية العمق بين الكرة والمرمى + افتكاك عند الاقتراب
            val retreatX = ballX * 0.78f
            val retreatY = ballY + (h * 0.50f - ballY) * 0.25f
            val defendAngle = atan2((retreatY - playerY).toDouble(), (retreatX - playerX).toDouble())

            if (distToBall < 0.10f) {
                val chaseAngle = atan2((ballY - playerY).toDouble(), (ballX - playerX).toDouble())
                bot.steerJoystick(joyX, joyY, chaseAngle, 1.0f)
                bot.shortPass(passX, passY)
            } else {
                bot.steerJoystick(joyX, joyY, defendAngle, 1.0f)
                if (distToBall > 0.28f) bot.sprintTap(dashX, dashY)
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "efootball_pro_channel"
        val channel = NotificationChannel(channelId, "eFootball Pro AI", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("eFootball Pro AI")
            .setContentText("الزر العائم جاهز فوق اللعبة")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingBtn?.let { windowManager?.removeView(it) }
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}