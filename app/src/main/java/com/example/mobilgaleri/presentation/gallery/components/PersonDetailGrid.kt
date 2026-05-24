package com.example.mobilgaleri.presentation.gallery.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.mobilgaleri.domain.model.Photo
import com.example.mobilgaleri.R

@Composable
fun PersonDetailGrid(
    state: LazyGridState,
    photos: List<Photo>,
    onPhotoClick: (Photo) -> Unit
) {
    val variant = colorResource(R.color.surface_variant)

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(top = 72.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = photos,
            key = { photo -> "${photo.id}_${photo.faceId}_${photo.contentUri}" }
        ) { photo ->
            Image(
                painter = rememberAsyncImagePainter(photo.contentUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(variant)
                    .clickable { onPhotoClick(photo) }
            )
        }
    }
}