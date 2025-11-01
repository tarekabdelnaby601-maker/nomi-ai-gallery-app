// com/example/mobilgaleri/presentation/gallery/GalleryScreen.kt
package com.example.mobilgaleri.presentation.gallery

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.mobilgaleri.domain.model.PersonDetails
import com.example.mobilgaleri.domain.model.Photo
import com.example.mobilgaleri.util.rememberPermissionStateCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(presenter: GalleryContract.Presenter) {
    val context = LocalContext.current
    val storagePermissionState = rememberPermissionStateCompat(
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    )
    val contactsPermissionState = rememberPermissionStateCompat(Manifest.permission.READ_CONTACTS)

    var clusters by remember { mutableStateOf<Map<Int, List<Photo>>>(emptyMap()) }
    var personDetails by remember { mutableStateOf<Map<Int, PersonDetails>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }
    var showMatchPrompt by remember { mutableStateOf(false) } // UI: bottom sheet tetikleyici

    val viewImpl = remember {
        object : GalleryContract.View {
            override fun showLoading(isLoading: Boolean) { loading = isLoading }
            override fun showError(message: String) { error = message }
            override fun updateProgress(processed: Int, total: Int) { progress = processed to total }
            override fun hideProgress() { progress = null }

            // UI: tek noktadan cluster + detay güncelle
            override fun displayClusters(
                newClusters: Map<Int, List<Photo>>,
                newDetails: Map<Int, PersonDetails>
            ) {
                clusters = newClusters
                personDetails = newDetails
            }

            // UI: özet toast
            override fun showSummaryToast(message: String) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }

            // UI: eşleştirme onay sayfası (bottom sheet)
            override fun showContactsMatchPrompt() { showMatchPrompt = true }
        }
    }

    LaunchedEffect(Unit) { presenter.attach(viewImpl) }
    DisposableEffect(Unit) { onDispose { presenter.detach() } }

    // İzin akışı (UI davranışı değişmedi)
    LaunchedEffect(storagePermissionState.hasPermission) {
        if (storagePermissionState.hasPermission) {
            if (!contactsPermissionState.hasPermission) contactsPermissionState.launchPermissionRequest()
        } else {
            storagePermissionState.launchPermissionRequest()
        }
    }
    LaunchedEffect(storagePermissionState.hasPermission, contactsPermissionState.hasPermission) {
        if (storagePermissionState.hasPermission && contactsPermissionState.hasPermission) {
            presenter.loadAndSyncClusters()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("MobilGaleri") }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {

                // UI: üstte ince ilerleme şeridi + metin
                if (progress != null && progress!!.second > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Yeni fotoğraflar taranıyor: ${progress!!.first} / ${progress!!.second}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = progress!!.first.toFloat() / progress!!.second.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        error != null -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Hata: $error", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                // UI: marka rengi
                                CircularProgressIndicator(color = Color(0xFF54B1FF))
                            }
                        }
                        clusters.isNotEmpty() -> {
                            ClusteredList(
                                map = clusters,
                                details = personDetails,
                                onPhotoClick = { photo -> selectedPhotoUri = photo.contentUri }
                            )
                        }
                        else -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Henüz gruplanmış kişi bulunamadı.")
                            }
                        }
                    }
                }
            }
        }

        // UI: tam ekran fotoğraf
        if (selectedPhotoUri != null) {
            FullScreenPhotoViewer(
                photoUri = selectedPhotoUri!!,
                onDismiss = { selectedPhotoUri = null }
            )
        }

        // UI: Ortada kartlı scanning overlay (yüzde ile)
        progress?.let { (done, total) ->
            if (total > 0) {
                val pct = (done * 100f / total).coerceIn(0f, 100f)
                ScanningOverlay(percent = pct)
            }
        }

        // UI: Eşleştirme onay bottom sheet
        if (showMatchPrompt) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showMatchPrompt = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sırada bu kişi içeren fotoğrafları rehberinizle eşleştirme var",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Rehber adlarıyla eşleştirme yapacağız. Onaylarsanız şimdi başlayalım.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showMatchPrompt = false
                            presenter.matchFacesWithContacts() // iş mantığına dokunmadan çağırıyoruz
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF54B1FF),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("Şimdi başla") }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/* ---------- UI yardımcı composable'lar ---------- */

@Composable
private fun ClusteredList(
    map: Map<Int, List<Photo>>,
    details: Map<Int, PersonDetails>,
    onPhotoClick: (Photo) -> Unit
) {
    if (map.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Henüz gruplanmış kişi bulunamadı.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        map.toSortedMap().forEach { (cid, list) ->
            if (cid >= 0) {
                val detail = details[cid]

                item(key = "header_$cid") {
                    Column {
                        Text(
                            text = "${detail?.displayName ?: "Kişi ${cid + 1}"} (${list.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        detail?.matchInfo?.let { info ->
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                item(key = "row_$cid") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { photo ->
                            Card(onClick = { onPhotoClick(photo) }) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = photo.contentUri),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenPhotoViewer(photoUri: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = photoUri),
                contentDescription = "Tam Ekran Fotoğraf",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun ScanningOverlay(percent: Float) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = cardElevation(16.dp),
                colors = cardColors(containerColor = Color.White)
            ) {
                Column(
                    Modifier
                        .padding(24.dp)
                        .widthIn(min = 260.dp, max = 360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = percent / 100f,
                        strokeWidth = 8.dp,
                        trackColor = Color(0xFFE0ECFF),
                        color = Color(0xFF54B1FF),
                        modifier = Modifier.size(84.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Galeri taranıyor, lütfen bekleyiniz…")
                    Spacer(Modifier.height(6.dp))
                    Text("${percent.toInt()}% tamamlandı", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
