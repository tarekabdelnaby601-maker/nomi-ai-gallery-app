//com/example/mobilgaleri/data/local/dao/FaceDao.kt
package com.example.mobilgaleri.data.local.dao

import androidx.room.*
import com.example.mobilgaleri.data.local.entity.FaceEntity

@Dao
interface FaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FaceEntity)

    @Update
    suspend fun updateAll(faces: List<FaceEntity>)

    @Query("SELECT DISTINCT photoOwnerId FROM faces")
    suspend fun getAllScannedPhotoIds(): List<Long>

    @Query("SELECT * FROM faces WHERE personOwnerId IN (:personIds)")
    suspend fun getFacesWithPersonIds(personIds: List<Long>): List<FaceEntity>

    @Query("SELECT * FROM faces")
    suspend fun all(): List<FaceEntity>

    // 🔥 DOĞRU HALİ — Artık id hatası yok
    @Query("UPDATE faces SET personOwnerId = :newPersonId WHERE faceId = :faceId")
    suspend fun updateFacePerson(faceId: Long, newPersonId: Long)

    @Query("SELECT * FROM faces WHERE faceId = :faceId LIMIT 1")
    suspend fun getById(faceId: Long): FaceEntity?

}

