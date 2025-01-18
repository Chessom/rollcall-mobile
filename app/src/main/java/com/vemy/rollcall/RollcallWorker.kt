package com.vemy.rollcall
/*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.Request

class RollcallWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    private val channelId = "rollcall_channel_id"

    init {
        // 初始化通知渠道
        createNotificationChannel()
    }

    override fun doWork(): Result {
        try {
            // 访问 rollcall API
            val rollcallUrl = "https://courses.zju.edu.cn/api/radar/rollcall"
            val request = Request.Builder().url(rollcallUrl).build()

            val response = HttpClientManager.client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d("RollcallWorker", "API call successful")
                sendNotification("Rollcall Success", "The rollcall API call was successful.")
            } else {
                Log.e("RollcallWorker", "API call failed: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("RollcallWorker", "Error during API call", e)
        }
        return Result.success()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Rollcall Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for rollcall API"
        }

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 替换为你的应用图标
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(1, notification)
    }
}
*/
