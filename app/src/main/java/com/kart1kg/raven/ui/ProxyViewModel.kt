package com.kart1kg.raven.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.kart1kg.raven.data.ServerState
import com.kart1kg.raven.service.SocksProxyService
import com.kart1kg.raven.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val server get() = SocksProxyService.server

    /** Observe the SOCKS5 server's state (running, connection count, etc.) */
    val serverState: StateFlow<ServerState> = server.serverState

    private val _portText = MutableStateFlow("1080")
    val portText: StateFlow<String> = _portText.asStateFlow()

    private val _hotspotIp = MutableStateFlow<String?>(null)
    val hotspotIp: StateFlow<String?> = _hotspotIp.asStateFlow()

    fun updatePort(text: String) {
        // Only allow digits, max 5 characters
        if (text.length <= 5 && text.all { it.isDigit() }) {
            _portText.value = text
        }
    }

    fun toggleProxy() {
        if (serverState.value.isRunning) {
            stopProxy()
        } else {
            startProxy()
        }
    }

    private fun startProxy() {
        val port = _portText.value.toIntOrNull() ?: 1080
        if (port !in 1..65535) return

        val context = getApplication<Application>()
        val intent = Intent(context, SocksProxyService::class.java).apply {
            putExtra(SocksProxyService.EXTRA_PORT, port)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        refreshHotspotIp()
    }

    private fun stopProxy() {
        val context = getApplication<Application>()
        val intent = Intent(context, SocksProxyService::class.java).apply {
            action = SocksProxyService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun refreshHotspotIp() {
        _hotspotIp.value = NetworkUtils.getHotspotIpAddress()
    }
}
