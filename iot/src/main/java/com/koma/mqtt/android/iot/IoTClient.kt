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

import android.content.Context
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

open class IoTClient internal constructor(private val builder: Builder) : IoT, MqttCallback {
    @get:JvmName("brokerUrl")
    val brokerUrl: String = builder.brokerUrl

    @get:JvmName("clientId")
    val clientId: String = builder.clientId

    @get:JvmName("context")
    val context: Context = builder.context

    @get:JvmName("automaticReconnect")
    val automaticReconnect: Boolean = builder.automaticReconnect

    @get:JvmName("cleanSession")
    val cleanSession: Boolean = builder.cleanSession

    @get:JvmName("keepAliveInterval")
    val keepAliveInterval: Int = builder.keepAliveInterval

    private val client =
        MqttAsyncClient(
            brokerUrl,
            clientId,
            MemoryPersistence(),
            AlarmPingSender(context)
        ).apply {
            setCallback(this@IoTClient)
        }

    override fun connect(userName: String, password: String) {
        val options = buildConnectOptions()

        options.userName = userName
        options.password = password.toCharArray()

        client.connect(options)
    }

    private fun buildConnectOptions() = MqttConnectOptions().apply {
        this.isAutomaticReconnect = this@IoTClient.automaticReconnect
        this.isCleanSession = this@IoTClient.cleanSession
        this.keepAliveInterval = this@IoTClient.keepAliveInterval
    }

    override fun isConnected() = client.isConnected

    override fun disConnect() {
        client.disconnect()
    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
    }

    override fun connectionLost(cause: Throwable?) {
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {
    }

    class Builder constructor() {
        internal lateinit var brokerUrl: String
        internal lateinit var clientId: String

        internal lateinit var context: Context

        internal var cleanSession = true
        internal var automaticReconnect = true

        internal var keepAliveInterval = ALIVE_INTERVAL

        fun brokerUrl(brokerUrl: String) = apply {
            this.brokerUrl = brokerUrl
        }

        fun clientId(clientId: String) = apply {
            this.clientId = clientId
        }

        fun context(context: Context) = apply {
            this.context = context.applicationContext
        }

        fun cleanSession(cleanSession: Boolean) = apply {
            this.cleanSession = true
        }

        fun automaticReconnect(automaticReconnect: Boolean) = apply {
            this.automaticReconnect = automaticReconnect
        }

        fun keepAliveInterval(keepAliveInterval: Int) = apply {
            this.keepAliveInterval = keepAliveInterval
        }

        fun build(): IoTClient = IoTClient(this)
    }

    companion object {
        private const val ALIVE_INTERVAL = 60
    }
}
