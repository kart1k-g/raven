package com.kart1kg.raven.engine

import android.util.Log
import com.kart1kg.raven.data.ServerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "Socks5Server"
private const val MAX_RECENT_CONNECTIONS = 50

/**
 * SOCKS5 proxy server that listens on a configurable port and handles
 * incoming connections using [Socks5Connection].
 *
 * Exposes [serverState] as a [StateFlow] for UI observation.
 */
class Socks5Server {

    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private val connectionIdCounter = AtomicLong(0)

    private val _serverState = MutableStateFlow(ServerState())
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    /**
     * Starts the SOCKS5 server on the given port, bound to all interfaces.
     * Non-blocking — the accept loop runs on [Dispatchers.IO].
     */
    fun start(port: Int) {
        if (serverSocket != null) {
            Log.w(TAG, "Server already running")
            return
        }

        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = serverScope

        serverScope.launch {
            try {
                val socket = ServerSocket(port)
                serverSocket = socket

                _serverState.value = _serverState.value.copy(
                    isRunning = true,
                    port = port,
                    activeConnections = 0
                )

                Log.i(TAG, "SOCKS5 server listening on port $port")

                while (!socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        val clientAddr = clientSocket.remoteSocketAddress.toString()

                        val connId = connectionIdCounter.incrementAndGet()
                        val connInfo = ConnectionInfo(
                            id = connId,
                            clientAddress = clientAddr,
                            destinationHost = "",
                            destinationPort = 0
                        )

                        // Track active connection count
                        updateActiveCount(1)
                        addRecentConnection(connInfo)

                        // Handle each connection in its own coroutine (supervised)
                        serverScope.launch {
                            try {
                                val handler = Socks5Connection(
                                    clientSocket = clientSocket,
                                    connectionInfo = connInfo,
                                    onConnectionUpdated = { updated ->
                                        updateRecentConnection(updated)
                                    }
                                )
                                handler.handle()
                            } finally {
                                updateActiveCount(-1)
                            }
                        }
                    } catch (_: SocketException) {
                        // Server socket closed during accept — normal shutdown
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            } finally {
                _serverState.value = _serverState.value.copy(isRunning = false)
                Log.i(TAG, "SOCKS5 server stopped")
            }
        }
    }

    /**
     * Stops the server and cancels all active connections.
     */
    fun stop() {
        Log.i(TAG, "Stopping SOCKS5 server...")
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope?.cancel()
        scope = null
        _serverState.value = ServerState()
    }

    private fun updateActiveCount(delta: Int) {
        val current = _serverState.value
        _serverState.value = current.copy(
            activeConnections = (current.activeConnections + delta).coerceAtLeast(0),
            totalConnections = if (delta > 0) current.totalConnections + 1 else current.totalConnections
        )
    }

    private fun addRecentConnection(info: ConnectionInfo) {
        val current = _serverState.value
        val updated = (listOf(info) + current.recentConnections).take(MAX_RECENT_CONNECTIONS)
        _serverState.value = current.copy(recentConnections = updated)
    }

    private fun updateRecentConnection(info: ConnectionInfo) {
        val current = _serverState.value
        val updated = current.recentConnections.map {
            if (it.id == info.id) info.copy() else it
        }
        _serverState.value = current.copy(recentConnections = updated)
    }
}