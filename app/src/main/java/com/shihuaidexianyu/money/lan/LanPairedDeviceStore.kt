package com.shihuaidexianyu.money.lan

import kotlinx.coroutines.flow.Flow

/**
 * Persistence port for paired LAN devices (session.device.v1). The server only ever sees
 * credential hashes; plaintext credentials are issued once at pairing time and held solely by
 * the client. Kept as a narrow interface so server unit tests stay hermetic.
 */
interface LanPairedDeviceStore {
    fun observeDevices(): Flow<List<LanPairedDevice>>
    suspend fun find(deviceId: String): LanPairedDevice?
    suspend fun upsert(device: LanPairedDevice)
    suspend fun remove(deviceId: String)
    suspend fun touchLastSeen(deviceId: String, seenAt: Long)
}

data class LanPairedDevice(
    val deviceId: String,
    val clientName: String,
    /** SHA-256 hex of the device credential. */
    val credentialHash: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
)
