package com.kart1kg.raven.data

import com.kart1kg.raven.engine.ConnectionInfo

/**
 * Immutable snapshot of the server's current state, observed by the UI.
 */
data class ServerState(
    val isRunning: Boolean = false,
    val port: Int = 1080,
    val activeConnections: Int = 0,
    val totalConnections: Long = 0,
    val recentConnections: List<ConnectionInfo> = emptyList()
)
