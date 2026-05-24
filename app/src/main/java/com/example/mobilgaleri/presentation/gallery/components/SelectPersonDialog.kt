package com.example.mobilgaleri.presentation.gallery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.example.mobilgaleri.R
import com.example.mobilgaleri.domain.model.PersonChoice

@Composable
fun SelectPersonDialog(
    faceId: Long,
    persons: List<PersonChoice>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedPersonId by remember { mutableStateOf<Long?>(null) }

    val primary = colorResource(R.color.button_primary)
    val onPrimary = colorResource(R.color.on_primary)
    val text = colorResource(R.color.text_primary)
    val dialogBackground = colorResource(R.color.dialog_background)
    val selectedRow = colorResource(R.color.selected_row)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBackground,
        title = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(primary)
                    .padding(16.dp)
            ) {
                Text(
                    "Fotoğrafı Kişiye Taşı",
                    color = onPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(persons) { p ->
                        val isSelected = selectedPersonId == p.personId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) selectedRow else dialogBackground
                                )
                                .clickable { selectedPersonId = p.personId }
                                .padding(16.dp)
                        ) {
                            Text(
                                text = p.displayName,
                                color = text
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        selectedPersonId?.let(onConfirm)
                    },
                    enabled = selectedPersonId != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Seç")
                }
            }
        },
        confirmButton = {}
    )
}