package com.kart1kg.raven.engine

import android.util.Log
import com.kart1kg.raven.engine.Socks5Constants.ATYP_DOMAIN
import com.kart1kg.raven.engine.Socks5Constants.ATYP_IPV4
import com.kart1kg.raven.engine.Socks5Constants.ATYP_IPV6
import com.kart1kg.raven.engine.Socks5Constants.AUTH_NO_ACCEPTABLE
import com.kart1kg.raven.engine.Socks5Constants.AUTH_NO_AUTH
import com.kart1kg.raven.engine.Socks5Constants.CMD_CONNECT
import com.kart1kg.raven.engine.Socks5Constants.CONNECT_TIMEOUT_MS
import com.kart1kg.raven.engine.Socks5Constants.RELAY_BUFFER_SIZE
import com.kart1kg.raven.engine.Socks5Constants.REP_CMD_NOT_SUPPORTED
import com.kart1kg.raven.engine.Socks5Constants.REP_CONNECTION_REFUSED
import com.kart1kg.raven.engine.Socks5Constants.REP_GENERAL_FAILURE
import com.kart1kg.raven.engine.Socks5Constants.REP_HOST_UNREACHABLE
import com.kart1kg.raven.engine.Socks5Constants.REP_NETWORK_UNREACHABLE
import com.kart1kg.raven.engine.Socks5Constants.REP_SUCCESS
import com.kart1kg.raven.engine.Socks5Constants.RESERVED
import com.kart1kg.raven.engine.Socks5Constants.VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException

private const val TAG = "Socks5Connection"

/**
 * Handles a single SOCKS5 client connection from handshake through data relay.
 *
 * Flow:
 * 1. Client greeting → server auth method selection (no-auth)
 * 2. Client connect request → server connects to destination
 * 3. Bidirectional relay until either side closes
 */
class Socks5Connection(
    private val clientSocket: Socket,
    private val connectionInfo: ConnectionInfo,
    private val onConnectionUpdated: (ConnectionInfo) -> Unit
) {
    private var destinationSocket: Socket? = null

    /**
     * Runs the full SOCKS5 lifecycle for this connection.
     */
    suspend fun handle() = withContext(Dispatchers.IO) {
        try {
            val clientInput = clientSocket.getInputStream()
            val clientOutput = clientSocket.getOutputStream()

            // Phase 1: Greeting & auth negotiation
            if (!handleGreeting(clientInput, clientOutput)) return@withContext

            // Phase 2: Parse connect request and open destination socket
            if (!handleConnectRequest(clientInput, clientOutput)) return@withContext

            // Phase 3: Bidirectional data relay
            connectionInfo.status = ConnectionStatus.RELAYING
            onConnectionUpdated(connectionInfo)
            relay(clientInput, clientOutput)
        } catch (e: SocketException) {
            Log.d(TAG, "Connection ${connectionInfo.id} socket closed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Connection ${connectionInfo.id} error", e)
            connectionInfo.status = ConnectionStatus.ERROR
            onConnectionUpdated(connectionInfo)
        } finally {
            close()
            if (connectionInfo.status != ConnectionStatus.ERROR) {
                connectionInfo.status = ConnectionStatus.CLOSED
            }
            onConnectionUpdated(connectionInfo)
            Log.d(
                TAG,
                "Connection ${connectionInfo.id} finished: " +
                    "↑${connectionInfo.bytesUploaded} ↓${connectionInfo.bytesDownloaded} bytes"
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 1: Greeting
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Reads the client greeting, validates version, and selects no-auth.
     *
     * Client greeting format:
     *   +-----+----------+----------+
     *   | VER | NMETHODS | METHODS  |
     *   +-----+----------+----------+
     *   |  1  |    1     | 1 to 255 |
     *   +-----+----------+----------+
     */
    private fun handleGreeting(input: InputStream, output: OutputStream): Boolean {
        val version = input.read()
        if (version != (VERSION.toInt() and 0xFF)) {
            Log.w(TAG, "Connection ${connectionInfo.id}: unsupported version $version")
            return false
        }

        val nMethods = input.read()
        if (nMethods <= 0) return false

        val methods = ByteArray(nMethods)
        readFully(input, methods)

        // Check if client supports no-auth (0x00)
        val supportsNoAuth = methods.any { it == AUTH_NO_AUTH }
        if (supportsNoAuth) {
            // Server response: version + chosen method
            output.write(byteArrayOf(VERSION, AUTH_NO_AUTH))
            output.flush()
            return true
        } else {
            output.write(byteArrayOf(VERSION, AUTH_NO_ACCEPTABLE))
            output.flush()
            Log.w(TAG, "Connection ${connectionInfo.id}: no acceptable auth method")
            return false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 2: Connect Request
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parses the SOCKS5 connect request and establishes the destination connection.
     *
     * Request format:
     *   +-----+-----+-------+------+----------+----------+
     *   | VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
     *   +-----+-----+-------+------+----------+----------+
     *   |  1  |  1  | X'00' |  1   | Variable |    2     |
     *   +-----+-----+-------+------+----------+----------+
     */
    private fun handleConnectRequest(input: InputStream, output: OutputStream): Boolean {
        val version = input.read()
        if (version != (VERSION.toInt() and 0xFF)) return false

        val command = input.read().toByte()
        input.read() // Reserved byte, discard

        if (command != CMD_CONNECT) {
            sendReply(output, REP_CMD_NOT_SUPPORTED)
            Log.w(TAG, "Connection ${connectionInfo.id}: unsupported command $command")
            return false
        }

        // Parse destination address
        val addressType = input.read().toByte()
        val (destHost, destAddress) = when (addressType) {
            ATYP_IPV4 -> {
                val addr = ByteArray(4)
                readFully(input, addr)
                val ip = InetAddress.getByAddress(addr)
                ip.hostAddress!! to ip
            }
            ATYP_DOMAIN -> {
                val domainLength = input.read()
                val domainBytes = ByteArray(domainLength)
                readFully(input, domainBytes)
                val domain = String(domainBytes, Charsets.US_ASCII)
                // Resolve DNS on the phone (server-side resolution)
                val resolved = InetAddress.getByName(domain)
                domain to resolved
            }
            ATYP_IPV6 -> {
                val addr = ByteArray(16)
                readFully(input, addr)
                val ip = InetAddress.getByAddress(addr)
                ip.hostAddress!! to ip
            }
            else -> {
                sendReply(output, Socks5Constants.REP_ATYP_NOT_SUPPORTED)
                return false
            }
        }

        // Parse destination port (2 bytes, big-endian)
        val portHigh = input.read()
        val portLow = input.read()
        val destPort = (portHigh shl 8) or portLow

        Log.d(TAG, "Connection ${connectionInfo.id}: CONNECT to $destHost:$destPort")

        // Connect to destination
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(destAddress, destPort), CONNECT_TIMEOUT_MS)
            destinationSocket = socket

            // Send success reply with bound address
            val boundAddr = socket.localAddress.address
            val boundPort = socket.localPort
            sendReply(output, REP_SUCCESS, boundAddr, boundPort)
            true
        } catch (e: ConnectException) {
            Log.w(TAG, "Connection ${connectionInfo.id}: refused by $destHost:$destPort")
            sendReply(output, REP_CONNECTION_REFUSED)
            false
        } catch (e: NoRouteToHostException) {
            Log.w(TAG, "Connection ${connectionInfo.id}: no route to $destHost")
            sendReply(output, REP_NETWORK_UNREACHABLE)
            false
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Connection ${connectionInfo.id}: unknown host $destHost")
            sendReply(output, REP_HOST_UNREACHABLE)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Connection ${connectionInfo.id}: connect failed", e)
            sendReply(output, REP_GENERAL_FAILURE)
            false
        }
    }

    /**
     * Sends a SOCKS5 reply to the client.
     *
     * Reply format:
     *   +-----+-----+-------+------+----------+----------+
     *   | VER | REP |  RSV  | ATYP | BND.ADDR | BND.PORT |
     *   +-----+-----+-------+------+----------+----------+
     *   |  1  |  1  | X'00' |  1   | Variable |    2     |
     *   +-----+-----+-------+------+----------+----------+
     */
    private fun sendReply(
        output: OutputStream,
        replyCode: Byte,
        boundAddress: ByteArray? = null,
        boundPort: Int = 0
    ) {
        val addr = boundAddress ?: byteArrayOf(0, 0, 0, 0)
        val atyp = if (addr.size == 16) ATYP_IPV6 else ATYP_IPV4
        val reply = byteArrayOf(
            VERSION,
            replyCode,
            RESERVED,
            atyp,
            *addr,
            (boundPort shr 8).toByte(),
            (boundPort and 0xFF).toByte()
        )
        output.write(reply)
        output.flush()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 3: Bidirectional Relay
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Relays data between client and destination using two concurrent coroutines.
     * When either direction encounters EOF or an error, both sockets are closed.
     */
    private suspend fun relay(
        clientInput: InputStream,
        clientOutput: OutputStream
    ) = coroutineScope {
        val destSocket = destinationSocket ?: return@coroutineScope
        val destInput = destSocket.getInputStream()
        val destOutput = destSocket.getOutputStream()

        // Client → Destination (upload)
        val uploadJob = launch(Dispatchers.IO) {
            try {
                val buffer = ByteArray(RELAY_BUFFER_SIZE)
                while (isActive) {
                    val bytesRead = clientInput.read(buffer)
                    if (bytesRead == -1) break
                    destOutput.write(buffer, 0, bytesRead)
                    destOutput.flush()
                    connectionInfo.bytesUploaded += bytesRead
                    onConnectionUpdated(connectionInfo)
                }
            } catch (_: SocketException) {
                // Connection closed
            } catch (e: Exception) {
                Log.d(TAG, "Upload relay error for ${connectionInfo.id}: ${e.message}")
            } finally {
                // Signal the other direction to stop
                runCatching { destSocket.shutdownOutput() }
            }
        }

        // Destination → Client (download)
        val downloadJob = launch(Dispatchers.IO) {
            try {
                val buffer = ByteArray(RELAY_BUFFER_SIZE)
                while (isActive) {
                    val bytesRead = destInput.read(buffer)
                    if (bytesRead == -1) break
                    clientOutput.write(buffer, 0, bytesRead)
                    clientOutput.flush()
                    connectionInfo.bytesDownloaded += bytesRead
                    onConnectionUpdated(connectionInfo)
                }
            } catch (_: SocketException) {
                // Connection closed
            } catch (e: Exception) {
                Log.d(TAG, "Download relay error for ${connectionInfo.id}: ${e.message}")
            } finally {
                runCatching { clientSocket.shutdownOutput() }
            }
        }

        // Wait for both directions to finish
        uploadJob.join()
        downloadJob.join()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────────────────

    private fun close() {
        runCatching { clientSocket.close() }
        runCatching { destinationSocket?.close() }
    }

    /**
     * Reads exactly [buffer.size] bytes from the stream, blocking until complete.
     */
    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = input.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) throw SocketException("Unexpected end of stream")
            offset += bytesRead
        }
    }
}
