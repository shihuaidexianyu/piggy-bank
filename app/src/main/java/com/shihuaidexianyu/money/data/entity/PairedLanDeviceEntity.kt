package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A computer paired with the LAN AI service (session.device.v1). Device-local only: the table
 * never travels with backups, and only the SHA-256 hash of the credential is stored — the
 * plaintext credential is handed to the client once at pairing time. Revoking a row forces the
 * device to pair again (with an on-phone confirmation) before it can resume a session.
 */
@Entity(tableName = "paired_lan_device")
data class PairedLanDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val clientName: String,
    /** SHA-256 hex of the device credential issued at pairing time. */
    val credentialHash: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
)
