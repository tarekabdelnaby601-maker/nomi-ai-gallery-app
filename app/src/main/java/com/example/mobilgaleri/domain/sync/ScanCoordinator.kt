package com.example.mobilgaleri.domain.sync

import com.example.mobilgaleri.data.local.dao.PhotoScanDao
import com.example.mobilgaleri.domain.model.Photo
import com.example.mobilgaleri.domain.repository.GalleryRepository

class ScanCoordinator(
    private val galleryRepository: GalleryRepository,
    private val photoScanDao: PhotoScanDao
) {
    suspend fun shouldSync(): Boolean {
        val devicePhotos = galleryRepository.getAllPhotos()
        val scannedIds = photoScanDao.getAllScannedPhotoIds().toSet()
        return devicePhotos.any { it.id !in scannedIds }
    }

    suspend fun getUnscannedPhotos(): List<Photo> {
        val devicePhotos = galleryRepository.getAllPhotos()
        val scannedIds = photoScanDao.getAllScannedPhotoIds().toSet()
        return devicePhotos.filter { it.id !in scannedIds }
    }
}