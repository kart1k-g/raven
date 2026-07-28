package com.kart1kg.raven.engine

/**
 * Tracks metadata for a single proxied connection.
 */
data class ConnectionInfo(
    val id: Long,
    val clientAddress: String,
    val destinationHost: String,
    val destinationPort: Int,
    val startTimeMs: Long = System.currentTimeMillis(),
    var bytesUploaded: Long = 0L,
    var bytesDownloaded: Long = 0L,
    var status: ConnectionStatus = ConnectionStatus.CONNECTING
)

enum class ConnectionStatus {
    CONNECTING,
    RELAYING,
    CLOSED,
    ERROR
}
