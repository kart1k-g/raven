package com.kart1kg.raven.engine

/**
 * SOCKS5 protocol constants as defined in RFC 1928.
 */
object Socks5Constants {
    // Protocol version
    const val VERSION: Byte = 0x05

    // Authentication methods
    const val AUTH_NO_AUTH: Byte = 0x00
    const val AUTH_NO_ACCEPTABLE: Byte = 0xFF.toByte()

    // Commands
    const val CMD_CONNECT: Byte = 0x01
    const val CMD_BIND: Byte = 0x02
    const val CMD_UDP_ASSOCIATE: Byte = 0x03

    // Address types
    const val ATYP_IPV4: Byte = 0x01
    const val ATYP_DOMAIN: Byte = 0x03
    const val ATYP_IPV6: Byte = 0x04

    // Reply codes
    const val REP_SUCCESS: Byte = 0x00
    const val REP_GENERAL_FAILURE: Byte = 0x01
    const val REP_NOT_ALLOWED: Byte = 0x02
    const val REP_NETWORK_UNREACHABLE: Byte = 0x03
    const val REP_HOST_UNREACHABLE: Byte = 0x04
    const val REP_CONNECTION_REFUSED: Byte = 0x05
    const val REP_TTL_EXPIRED: Byte = 0x06
    const val REP_CMD_NOT_SUPPORTED: Byte = 0x07
    const val REP_ATYP_NOT_SUPPORTED: Byte = 0x08

    // Reserved byte
    const val RESERVED: Byte = 0x00

    // Buffer size for relay (8 KB)
    const val RELAY_BUFFER_SIZE = 8192

    // Connection timeout in milliseconds
    const val CONNECT_TIMEOUT_MS = 10_000
}
