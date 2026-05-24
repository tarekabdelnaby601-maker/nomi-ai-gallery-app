//com/example/mobilgaleri/data/local/dao/PersonDao.kt
package com.example.mobilgaleri.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.mobilgaleri.data.local.entity.PersonEntity

@Dao
interface PersonDao {
    @Upsert
    suspend fun upsert(person: PersonEntity)

    @Query("SELECT * FROM persons")
    suspend fun getAll(): List<PersonEntity>

    @Delete
    suspend fun deleteAll(persons: List<PersonEntity>)

    // YENİ: Belirli bir kişinin rehber eşleşme bilgilerini güncelleyen sorgu
    @Query("UPDATE persons SET contactId = :contactId, contactMatchConfidence = :confidence WHERE personId = :personId")
    suspend fun updateContactMatch(personId: Long, contactId: Long, confidence: Float)

    @Query("UPDATE persons SET name = :contactName WHERE personId = :personId")
    suspend fun updateContactName(personId: Long, contactName: String)

}
