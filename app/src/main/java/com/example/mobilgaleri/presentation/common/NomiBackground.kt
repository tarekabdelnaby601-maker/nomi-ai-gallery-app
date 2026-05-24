package com.example.mobilgaleri.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.mobilgaleri.R

@Composable
fun NomiBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Çok silik full-screen yüz
        Image(
            painter = painterResource(id = R.drawable.bg_nomi_face),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            alpha = 0.06f // 🔹 siliklik seviyesi (0.03–0.08 arası oynayabilirsin)
        )

        // Üstüne asıl içerik
        content()
    }
}
