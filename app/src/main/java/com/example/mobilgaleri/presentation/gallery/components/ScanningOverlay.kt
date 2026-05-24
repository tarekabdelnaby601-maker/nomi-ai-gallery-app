package com.example.mobilgaleri.presentation.gallery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mobilgaleri.R

@Composable
fun ScanningOverlay(percent: Float) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val surface = colorResource(R.color.surface)
        val primary = colorResource(R.color.button_primary)
        val text = colorResource(R.color.text_primary)
        val overlay = colorResource(R.color.overlay)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlay),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(16.dp),
                colors = CardDefaults.cardColors(containerColor = surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(min = 260.dp, max = 360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { percent / 100f },
                        strokeWidth = 8.dp,
                        trackColor = Color.Transparent,
                        color = primary,
                        modifier = Modifier.size(84.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Galeri taranıyor...",
                        color = text
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${percent.toInt()}% tamamlandı",
                        style = MaterialTheme.typography.titleMedium,
                        color = text
                    )
                }
            }
        }
    }
}