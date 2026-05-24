package com.example.mobilgaleri.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mobilgaleri.data.local.entity.PhotoScanEntity

@Dao
interface PhotoScanDao {

    @Query("SELECT photoId FROM photo_scan")
    suspend fun getAllScannedPhotoIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: PhotoScanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scans: List<PhotoScanEntity>)
}