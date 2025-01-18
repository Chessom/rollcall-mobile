package com.vemy.rollcall

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var serviceSwitch: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var ringtoneSwitch: Switch
    private lateinit var testRingButton: Button
    private lateinit var stopRingButton: Button

    private lateinit var serviceStatusText: TextView
    private lateinit var intervalEditText: EditText
    private lateinit var intervalConfirmButton: Button

    private var isServiceRunning = false
    private var isRingtoneEnabled = true
    private lateinit var rollcallService: RollcallForegroundService
    private var ringtone: android.media.Ringtone? = null
    private var interval: Long = 10

    // Service connection to bind/unbind from the service
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("MainActivity", "Service connected")
            val binder = service as RollcallForegroundService.LocalBinder
            rollcallService = binder.getService()
            isServiceRunning = true
            updateServiceStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MainActivity", "Service disconnected")
            isServiceRunning = false
            updateServiceStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermission() // 请求权限

        // 初始化UI控件
        serviceSwitch = findViewById(R.id.service_switch)
        ringtoneSwitch = findViewById(R.id.ringtone_switch)
        testRingButton = findViewById(R.id.test_ring_button)
        stopRingButton = findViewById(R.id.stop_ring_button)
        intervalEditText = findViewById(R.id.intervalEditText)
        intervalConfirmButton = findViewById(R.id.intervalConfirm)
        serviceStatusText = findViewById(R.id.service_status)

        // 启动和绑定前台服务
        val serviceIntent = Intent(this, RollcallForegroundService::class.java)

        // 控制服务开关
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isServiceRunning) {
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                startService(serviceIntent)
            } else {
                if (isServiceRunning) {
                    // 解除绑定
                    unbindService(serviceConnection)
                    stopService(serviceIntent)
                    isServiceRunning = false
                    updateServiceStatus()
                }
            }
        }

        // 控制响铃开关
        ringtoneSwitch.setOnCheckedChangeListener { _, isChecked ->
            isRingtoneEnabled = isChecked
            if (isServiceRunning){
                rollcallService.setRingtoneEnabled(isRingtoneEnabled)
            }
        }

        // 测试响铃按钮
        testRingButton.setOnClickListener {
            if (ringtone == null) {
                val alarmUri =
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) // 系统闹铃音
                ringtone = RingtoneManager.getRingtone(this, alarmUri)
            }
            ringtone?.play()
        }

        // 停止响铃按钮
        stopRingButton.setOnClickListener {
            if (isServiceRunning) {
                rollcallService.stopAlarmSound() // 停止铃声
            }
            ringtone?.stop()
        }

        intervalConfirmButton.setOnClickListener {
            val str = intervalEditText.text.toString()
            interval = 10
            if (str.isNotEmpty()) {
                interval = str.toLong()
            }
            if (::rollcallService.isInitialized) {
                rollcallService.setInterval(interval)
            }
        }
    }

    private fun requestNotificationPermission() {
        // 检查是否已获得通知权限
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，则请求权限
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

    }


    private fun updateServiceStatus() {
        if (isServiceRunning) {
            rollcallService.setInterval(interval)
            rollcallService.setRingtoneEnabled(isRingtoneEnabled) // 控制服务内部响铃逻辑
            serviceStatusText.text = "正在运行"
            serviceSwitch.isChecked = true
        } else {
            serviceStatusText.text = "已停止"
            serviceSwitch.isChecked = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceRunning) {
            unbindService(serviceConnection)
        }
    }
}
