//com/example/mobilgaleri/domain/model/Photo.kt
package com.example.mobilgaleri.domain.model

data class Photo(
    val id: Long,              // photoOwnerId
    val contentUri: String,
    val dateTaken: Long?,
    val faceId: Long           // yeni eklendi 🔥
)

