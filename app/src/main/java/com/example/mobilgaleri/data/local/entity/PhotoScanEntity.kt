package com.example.mobilgaleri.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_scan")
data class PhotoScanEntity(
    @PrimaryKey
    val photoId: Long,
    val scannedAt: Long,
    val hasFace: Boolean
)