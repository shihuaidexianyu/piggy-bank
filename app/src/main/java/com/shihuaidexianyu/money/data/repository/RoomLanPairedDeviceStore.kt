package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.PairedLanDeviceDao
import com.shihuaidexianyu.money.data.entity.PairedLanDeviceEntity
import com.shihuaidexianyu.money.lan.LanPairedDevice
import com.shihuaidexianyu.money.lan.LanPairedDeviceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLanPairedDeviceStore(
    private val dao: PairedLanDeviceDao,
) : LanPairedDeviceStore {
    override fun observeDevices(): Flow<List<LanPairedDevice>> = dao.observeDevices().map { list ->
        list.map { it.toLanPairedDevice() }
    }

    override suspend fun find(deviceId: String): LanPairedDevice? =
        dao.findById(deviceId)?.toLanPairedDevice()

    override suspend fun upsert(device: LanPairedDevice) {
        dao.upsert(device.toEntity())
    }

    override suspend fun remove(deviceId: String) {
        dao.deleteById(deviceId)
    }

    override suspend fun touchLastSeen(deviceId: String, seenAt: Long) {
        dao.touchLastSeen(deviceId, seenAt)
    }
}

private fun PairedLanDeviceEntity.toLanPairedDevice() = LanPairedDevice(
    deviceId = deviceId,
    clientName = clientName,
    credentialHash = credentialHash,
    pairedAt = pairedAt,
    lastSeenAt = lastSeenAt,
)

private fun LanPairedDevice.toEntity() = PairedLanDeviceEntity(
    deviceId = deviceId,
    clientName = clientName,
    credentialHash = credentialHash,
    pairedAt = pairedAt,
    lastSeenAt = lastSeenAt,
)
