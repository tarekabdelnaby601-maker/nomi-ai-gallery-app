package com.example.mobilgaleri.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.mobilgaleri.data.local.dao.*
import com.example.mobilgaleri.data.local.entity.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PhotoEntity::class,
        FaceEntity::class,
        PersonEntity::class,
        FaceHistoryEntity::class,
        FaceClusterSimilarityEntity::class,
        PhotoScanEntity::class,
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun faceDao(): FaceDao
    abstract fun personDao(): PersonDao
    abstract fun faceHistoryDao(): FaceHistoryDao
    abstract fun faceClusterSimilarityDao(): FaceClusterSimilarityDao

    abstract fun photoScanDao(): PhotoScanDao
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // persons tablosuna yeni sütunlar
        db.execSQL("ALTER TABLE persons ADD COLUMN matchedContactId INTEGER")
        db.execSQL("ALTER TABLE persons ADD COLUMN matchConfidence REAL")

        // FaceHistory tablosunu oluştur
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS face_history (
                historyId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                faceId INTEGER NOT NULL,
                oldPersonId INTEGER NOT NULL,
                newPersonId INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS photo_scan (
                photoId INTEGER NOT NULL PRIMARY KEY,
                scannedAt INTEGER NOT NULL,
                hasFace INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
