//com/example/mobilgaleri/data/local/entity/PersonEntity.kt
package com.example.mobilgaleri.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey
    var personId: Long,           // <-- val → var yap
    var name: String? = null,
    val centroid: FloatArray,

    // YENİ: Rehberle eşleştirme için yeni sütunlar
    var contactId: Long? = null, // Eşleşen kişinin rehberdeki ID'si
    var contactMatchConfidence: Float? = null // Benzerlik oranı
)
