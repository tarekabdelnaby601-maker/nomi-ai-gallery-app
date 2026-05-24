package com.example.mobilgaleri.presentation.gallery.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.mobilgaleri.domain.model.PersonDetails
import com.example.mobilgaleri.domain.model.Photo
import com.example.mobilgaleri.R

@Composable
fun AlbumsGrid(
    state: LazyGridState,
    clusters: Map<Int, List<Photo>>,
    personDetails: Map<Int, PersonDetails>,
    onPersonClick: (Int) -> Unit
) {
    val personIds = clusters.keys.toList().distinct()

    val surface = colorResource(R.color.surface)
    val variant = colorResource(R.color.surface_variant)
    val text = colorResource(R.color.text_primary)
    val subText = colorResource(R.color.text_secondary)

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(top = 72.dp, start = 8.dp, end = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = personIds,
            key = { personId -> "person_$personId" }
        ) { personId ->
            val photos = clusters[personId] ?: emptyList()
            val detail = personDetails[personId]
            val coverPhoto = photos.firstOrNull()
            val name = detail?.displayName ?: "Kişi ${personId + 1}"

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onPersonClick(personId) }
                    .padding(4.dp)
            ) {
                Card(
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = surface)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(coverPhoto?.contentUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(variant)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "(${photos.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = subText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}