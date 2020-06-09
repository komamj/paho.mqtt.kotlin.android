/*
 * Copyright 2020 komamj
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.koma.mqtt.android.iot

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttPingSender
import org.eclipse.paho.client.mqttv3.internal.ClientComms

class AlarmPingSender internal constructor(private val context: Context) : MqttPingSender {
    private lateinit var clientComms: ClientComms

    private val alarmReceiver: BroadcastReceiver = AlarmReceiver()

    private var pendingIntent: PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ALARM_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    @Volatile
    private var hasStarted = false

    override fun init(clientComms: ClientComms) {
        this.clientComms = clientComms
    }

    override fun start() {
        context.registerReceiver(alarmReceiver, IntentFilter(ALARM_ACTION))

        schedule(clientComms.keepAlive)

        hasStarted = true
    }

    override fun stop() {
        if (!hasStarted) {
            return
        }

        val alarmManager = context.getSystemService(Service.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        hasStarted = false

        try {
            context.unregisterReceiver(alarmReceiver)
        } catch (e: Exception) {
            // Ignore unregister exceptions
        }
    }

    override fun schedule(delayInMilliseconds: Long) {
        val nextAlarmInMilliseconds = System.currentTimeMillis() + delayInMilliseconds

        val alarmManager = context.getSystemService(Service.ALARM_SERVICE) as AlarmManager

        when {
            Build.VERSION.SDK_INT >= 23 -> {
                // In SDK 23 and above, dosing will prevent setExact, setExactAndAllowWhileIdle will force
                // the device to run this task whilst dosing.
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmInMilliseconds,
                    pendingIntent
                )
            }
            Build.VERSION.SDK_INT >= 19 -> {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmInMilliseconds,
                    pendingIntent
                )
            }
            else -> {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pendingIntent)
            }
        }
    }

    /*
     * This class sends PingReq packet to MQTT broker
     */
    internal inner class AlarmReceiver : BroadcastReceiver() {
        private var wakelock: WakeLock? = null

        private val wakeLockTag = "IoT" + clientComms.client.clientId

        @SuppressLint("Wakelock", "WakelockTimeout")
        override fun onReceive(context: Context, intent: Intent) {
            // According to the docs, "Alarm Manager holds a CPU wake lock as
            // long as the alarm receiver's onReceive() method is executing.
            // This guarantees that the phone will not sleep until you have
            // finished handling the broadcast.", but this class still get
            // a wake lock to wait for ping finished.
            val powerManager = context.getSystemService(Service.POWER_SERVICE) as PowerManager

            wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag)
            wakelock?.acquire()

            // Assign new callback to token to execute code after PingResq
            // arrives. Get another wakelock even receiver already has one,
            // release it until ping response returns.
            val token: IMqttToken? = clientComms.checkForActivity(object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    // Release wakelock when it is done.
                    wakelock?.release()
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    // Release wakelock when it is done.
                    wakelock?.release()
                }
            })

            token?.run {
                wakelock?.run {
                    if (isHeld) {
                        release()
                    }
                }
            }
        }
    }

    companion object {
        private const val ALARM_ACTION = "com.koma.mqtt.android.iot.AlarmPingSender"
    }
}
