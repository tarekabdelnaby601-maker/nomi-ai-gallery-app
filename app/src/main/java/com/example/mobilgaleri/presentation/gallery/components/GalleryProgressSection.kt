package com.example.mobilgaleri.presentation.gallery.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GalleryProgressSection(
    done: Int,
    total: Int
) {
    if (total <= 0) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Yeni fotoğraflar taranıyor: $done / $total")
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier.fillMaxWidth()
        )
    }
}