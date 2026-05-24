package com.example.mobilgaleri.data.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.mobilgaleri.domain.model.Photo
import com.example.mobilgaleri.domain.repository.GalleryRepository

class GalleryRepositoryImpl(
    private val contentResolver: ContentResolver
) : GalleryRepository {

    override suspend fun getAllPhotos(): List<Photo> {
        val photos = mutableListOf<Photo>()

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val cursor = contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val date = c.getLong(dateCol)
                val uri = ContentUris.withAppendedId(collection, id)

                photos.add(
                    Photo(
                        id = id,                        // photoOwnerId
                        contentUri = uri.toString(),    // görüntü yolu
                        dateTaken = date,
                        faceId = -1L                    // YENİ — başlangıçta yok
                    )
                )
            }
        }

        return photos
    }
}
