package com.vemy.rollcall

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

class RollcallForegroundService : Service() {

    private val channelId = "rollcall_channel_id"
    private var ringtone: android.media.Ringtone? = null
    private var ringtoneEnabled = true
    private var interval: AtomicLong = AtomicLong(10)
    private var mBinder: LocalBinder = LocalBinder()

    private val handler = Handler()
    private val taskRunnable = object : Runnable {
        override fun run() {
            performTask() // 执行具体任务
            handler.postDelayed(this, interval.get() * 1000) // 每隔 10 秒执行一次任务
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): RollcallForegroundService = this@RollcallForegroundService

    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundNotification()
        handler.post(taskRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(taskRunnable)
        stopAlarmSound()  // 停止铃声
        super.onDestroy()
    }

    private fun performTask() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 访问 API
                val rollcallUrl = "https://courses.zju.edu.cn/api/radar/rollcalls"
                //val rollcallUrl = "http://192.168.110.19:8888/rollcalls"
                val request = Request.Builder().url(rollcallUrl).build()
                val response = HttpClientManager.client.newCall(request).execute()

                if (!response.isSuccessful) {
                    // 网络请求失败，发送通知并响铃
                    Log.e("RollcallService", "API call failed: ${response.message}")
                    showNotification("Network Error", "Unable to access the rollcall API.")
                    playAlarmSound()
                    return@launch
                }

                val responseBody = response.body?.string() ?: ""
                if (responseBody.isBlank()) {
                    // 如果 response 为空，不做任何处理
                    Log.d("RollcallService", "Empty response body")
                    return@launch
                }

                // 解析 JSON
                try {
                    val jsonResponse = JSONObject(responseBody)
                    val rollcallsArray = jsonResponse.optJSONArray("rollcalls")

                    if (rollcallsArray != null && rollcallsArray.length() > 0) {
                        // rollcalls 不为空，发通知并响铃
                        val rollcall = rollcallsArray.getJSONObject(0)
                        val courseTitle = rollcall.optString("course_title", "No Course Title")

                        showNotification(
                            "New Rollcall: $courseTitle",
                            "The rollcall is in progress!"
                        )
                        playAlarmSound()
                    } else {
                        // rollcalls 为空，不做任何处理
                        Log.d("RollcallService", "Rollcalls array is empty")
                    }
                } catch (e: Exception) {
                    Log.e("RollcallService", "Error parsing response JSON", e)
                    showNotification("Parsing Error", "Error parsing response JSON")
                }
            } catch (e: Exception) {
                Log.e("RollcallService", "Error during API call", e)
                // 捕获网络错误，例如超时等
                showNotification("Network Error", "Unable to access the rollcall API.")
                playAlarmSound()
            }
        }
    }

    public fun setInterval(intervalSeconds: Long) {
        if (intervalSeconds <= 2) {
            interval.set(2)
        } else {
            interval.set(intervalSeconds)
        }
    }

    private fun createNotificationChannel() {
        val name = "Rollcall Service"
        val descriptionText = "Channel for Rollcall service notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
            enableLights(true)
            lightColor = android.graphics.Color.RED
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 100, 500)
        }
        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("ForegroundServiceType", "LaunchActivityFromNotification")
    private fun startForegroundNotification() {
        val stopIntent = Intent(this, RollcallForegroundService::class.java).apply {
            action = "STOP_ALARM"
        }
        val stopPendingIntent: PendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rollcall Service")
            .setContentText("Running periodic rollcall task...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(stopPendingIntent, true) // 确保显示 Heads-up 通知
            .setOngoing(true) // 设置通知为常驻
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Alarm",
                stopPendingIntent
            ) // 停止按钮
            .build()

        startForeground(1, notification)
    }

    private fun showNotification(title: String, content: String) {
        val stopIntent = Intent(this, RollcallForegroundService::class.java).apply {
            action = "STOP_ALARM"
        }
        val stopPendingIntent: PendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 高优先级
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 默认声音、振动等
            .setCategory(NotificationCompat.CATEGORY_ALARM) // 分类为消息通知
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Alarm",
                stopPendingIntent
            ) // 停止按钮
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    public fun playAlarmSound() {
        if (ringtone == null) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) // 系统闹铃音
            ringtone = RingtoneManager.getRingtone(this, alarmUri)
        }
        if (ringtoneEnabled) {
            ringtone?.play()
        }
    }

    public fun stopAlarmSound() {
        ringtone?.stop()
        ringtone = null
    }

    // 处理停止闹铃的意图
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALARM") {
            stopAlarmSound()
            stopSelf() // 停止服务
        }
        return START_STICKY
    }

    fun setRingtoneEnabled(isEnabled: Boolean) {
        ringtoneEnabled = isEnabled
    }
}
