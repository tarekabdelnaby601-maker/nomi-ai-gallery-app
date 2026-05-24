package com.example.mobilgaleri.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_history")
data class FaceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val faceId: Long,          // Hangi yüz
    val oldPersonId: Long?,    // Önce hangi kişideydi (ilk atamada null olabilir)
    val newPersonId: Long,     // Şimdi hangi kişiye taşındı
    val changedAt: Long        // Zaman damgası (System.currentTimeMillis())
)
