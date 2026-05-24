// com/example/mobilgaleri/di/AppModule.kt
package com.example.mobilgaleri.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.example.mobilgaleri.data.face.MediaPipeFaceDetector
import com.example.mobilgaleri.data.face.OnnxEdgeFaceEmbedder
import com.example.mobilgaleri.data.gallery.GalleryRepositoryImpl
import com.example.mobilgaleri.data.local.dao.FaceDao
import com.example.mobilgaleri.data.local.dao.PersonDao
import com.example.mobilgaleri.data.local.db.AppDatabase
import com.example.mobilgaleri.data.local.db.MIGRATION_2_3
import com.example.mobilgaleri.data.repository.ContactsRepositoryImpl
import com.example.mobilgaleri.data.repository.FaceRepositoryImpl
import com.example.mobilgaleri.data.repository.PersonRepositoryImpl
import com.example.mobilgaleri.domain.repository.ContactsRepository
import com.example.mobilgaleri.domain.repository.FaceRepository
import com.example.mobilgaleri.domain.repository.GalleryRepository
import com.example.mobilgaleri.domain.repository.PersonRepository
import com.example.mobilgaleri.domain.usecase.*
import com.example.mobilgaleri.data.face.MediaPipeFaceLandmarker
import com.example.mobilgaleri.data.local.db.MIGRATION_3_4
import com.example.mobilgaleri.data.repository.FaceClusterSimilarityRepositoryImpl
import com.example.mobilgaleri.data.repository.FaceHistoryRepositoryImpl
import com.example.mobilgaleri.domain.repository.FaceClusterSimilarityRepository
import com.example.mobilgaleri.domain.repository.FaceHistoryRepository
import com.example.mobilgaleri.domain.sync.ScanCoordinator

object AppModule {

    // ----------------------------------
    // DATABASE SINGLETON
    // ----------------------------------

    @Volatile
    private var INSTANCE: AppDatabase? = null

    @Synchronized
    fun provideDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "mobilgaleri.db"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()

            INSTANCE = instance
            instance
        }
    }

    // DAO’lar
    fun provideFaceDao(db: AppDatabase): FaceDao = db.faceDao()
    fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()

    // ----------------------------------
    // SYSTEM SERVICES
    // ----------------------------------

    fun provideContentResolver(context: Context): ContentResolver = context.contentResolver

    // ----------------------------------
    // REPOSITORIES
    // ----------------------------------

    fun provideGalleryRepository(cr: ContentResolver): GalleryRepository =
        GalleryRepositoryImpl(cr)

    fun provideContactsRepository(cr: ContentResolver): ContactsRepository =
        ContactsRepositoryImpl(cr)

    fun provideFaceRepository(faceDao: FaceDao): FaceRepository =
        FaceRepositoryImpl(faceDao)

    fun providePersonRepository(personDao: PersonDao): PersonRepository =
        PersonRepositoryImpl(personDao)

    fun provideFaceHistoryRepository(context: Context): FaceHistoryRepository {
        val db = provideDatabase(context)
        return FaceHistoryRepositoryImpl(db.faceHistoryDao())
    }

    // ----------------------------------
    // AI MODELLERİ
    // ----------------------------------

    @Volatile private var mediaPipeDetector: MediaPipeFaceDetector? = null
    @Volatile private var onnxEmbedder: OnnxEdgeFaceEmbedder? = null
    @Volatile private var clusterer: ClusterFacesUseCase? = null
    @Volatile private var faceLandmarker: MediaPipeFaceLandmarker? = null

    @Synchronized
    fun provideFaceDetector(context: Context): MediaPipeFaceDetector {
        return mediaPipeDetector ?: MediaPipeFaceDetector(context.applicationContext).also {
            mediaPipeDetector = it
        }
    }

    @Synchronized
    fun provideEmbedder(context: Context): OnnxEdgeFaceEmbedder {
        return onnxEmbedder ?: OnnxEdgeFaceEmbedder(context.applicationContext).also {
            onnxEmbedder = it
        }
    }

    @Synchronized
    fun provideClusterer(): ClusterFacesUseCase {
        return clusterer ?: ClusterFacesUseCase().also { clusterer = it }
    }

    @Synchronized
    fun provideFaceLandmarker(context: Context): MediaPipeFaceLandmarker {
        return faceLandmarker ?: MediaPipeFaceLandmarker(context.applicationContext).also {
            faceLandmarker = it
        }
    }

    // ----------------------------------
    // USE CASES
    // ----------------------------------
    fun provideFaceClusterSimilarityRepository(db: AppDatabase): FaceClusterSimilarityRepository {
        return FaceClusterSimilarityRepositoryImpl(db.faceClusterSimilarityDao())
    }

    fun provideSyncAndClusterNewFacesUseCase(context: Context): SyncAndClusterNewFacesUseCase {
        val app = context.applicationContext
        val db = provideDatabase(app)
        val cr = provideContentResolver(app)

        return SyncAndClusterNewFacesUseCase(
            galleryRepo = provideGalleryRepository(cr),
            faceRepo = provideFaceRepository(db.faceDao()),
            personRepo = providePersonRepository(db.personDao()),
            cr = cr,
            detector = provideFaceDetector(app),
            embedder = provideEmbedder(app),
            onlineClusterer = provideClusterer(),
            landmarker = provideFaceLandmarker(app),
            faceClusterSimilarityRepo = provideFaceClusterSimilarityRepository(db),   // ← BUNU EKLE
            photoScanDao = db.photoScanDao(),
            scanCoordinator = provideScanCoordinator(app)
        )

    }

    fun provideGetSavedClustersUseCase(context: Context): GetSavedClustersUseCase {
        val app = context.applicationContext
        val db = provideDatabase(app)
        val cr = provideContentResolver(app)

        return GetSavedClustersUseCase(
            faceRepository = provideFaceRepository(db.faceDao()),
            galleryRepository = provideGalleryRepository(cr),
            personRepository = providePersonRepository(db.personDao())
        )
    }

    fun provideReassignFaceToPersonUseCase(context: Context): ReassignFaceToPersonUseCase {
        val db = provideDatabase(context)

        return ReassignFaceToPersonUseCase(
            faceRepository = FaceRepositoryImpl(db.faceDao()),
            historyRepository = FaceHistoryRepositoryImpl(db.faceHistoryDao())
        )
    }

    fun provideGetAllPersonsUseCase(context: Context): GetAllPersonsUseCase {
        val db = provideDatabase(context)
        return GetAllPersonsUseCase(PersonRepositoryImpl(db.personDao()))
    }
    fun provideFaceClusterSimilarityRepository(context: Context): FaceClusterSimilarityRepository {
        return FaceClusterSimilarityRepositoryImpl(
            provideDatabase(context).faceClusterSimilarityDao()
        )
    }

    fun provideMatchContactsUseCase(context: Context): MatchContactsUseCase {
        val app = context.applicationContext
        val db = provideDatabase(app)
        val cr = provideContentResolver(app)

        return MatchContactsUseCase(
            personRepository = providePersonRepository(db.personDao()),
            contactsRepository = provideContactsRepository(cr),
            embedder = provideEmbedder(app),
            contentResolver = cr,
            landmarker = provideFaceLandmarker(app)
        )
    }

    fun provideScanCoordinator(context: Context): ScanCoordinator {
        val app = context.applicationContext
        val db = provideDatabase(app)
        val cr = provideContentResolver(app)

        return ScanCoordinator(
            galleryRepository = provideGalleryRepository(cr),
            photoScanDao = db.photoScanDao()
        )
    }
}
