package com.example.mobilgaleri.presentation.gallery.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.example.mobilgaleri.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchContactsBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onStartClick: () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        val primary = colorResource(R.color.button_primary)
        val onPrimary = colorResource(R.color.on_primary)
        val text = colorResource(R.color.text_primary)
        val subText = colorResource(R.color.text_secondary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sırada bu kişi içeren fotoğrafları rehberinizle eşleştirme var",
                color = text
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Rehber adlarıyla eşleştirme yapacağız. Onaylarsanız şimdi başlayalım.",
                color = subText
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStartClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    contentColor = onPrimary
                )
            ) {
                Text("Şimdi başla")
            }
        }
    }
}