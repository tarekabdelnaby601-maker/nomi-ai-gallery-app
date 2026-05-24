package com.example.mobilgaleri.presentation.gallery

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.example.mobilgaleri.util.ThemePreferences
import com.example.mobilgaleri.R
import com.example.mobilgaleri.data.local.entity.PersonEntity
import com.example.mobilgaleri.domain.model.PersonChoice
import com.example.mobilgaleri.domain.model.PersonDetails
import com.example.mobilgaleri.domain.model.Photo
import com.example.mobilgaleri.presentation.gallery.components.AlbumsGrid
import com.example.mobilgaleri.presentation.gallery.components.FullScreenPhotoViewer
import com.example.mobilgaleri.presentation.gallery.components.GalleryProgressSection
import com.example.mobilgaleri.presentation.gallery.components.MatchContactsBottomSheet
import com.example.mobilgaleri.presentation.gallery.components.PersonDetailGrid
import com.example.mobilgaleri.presentation.gallery.components.ScanningOverlay
import com.example.mobilgaleri.presentation.gallery.components.SelectPersonDialog
import com.example.mobilgaleri.util.rememberPermissionStateCompat
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(presenter: GalleryContract.Presenter) {

    val context = LocalContext.current

    // İZİNLER
    val storagePermissionState = rememberPermissionStateCompat(
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    )
    val contactsPermissionState = rememberPermissionStateCompat(Manifest.permission.READ_CONTACTS)

    // STATE
    var clusters by remember { mutableStateOf<Map<Int, List<Photo>>>(emptyMap()) }
    var personDetails by remember { mutableStateOf<Map<Int, PersonDetails>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var viewingPersonId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedPhotoUri by rememberSaveable  { mutableStateOf<String?>(null) }
    var selectedFaceId by rememberSaveable  { mutableStateOf<Long?>(null) }
    var showMatchPrompt by rememberSaveable  { mutableStateOf(false) }
    var selectedPersonDialogData by remember { mutableStateOf<Pair<Long, List<PersonChoice>>?>(null) }

    // GRID STATE
    val gridState = rememberLazyGridState()

    // Sayfa değiştiğinde listeyi en başa sar
    LaunchedEffect(viewingPersonId) {
        gridState.scrollToItem(0)
    }

    // Scrollbar görünürlüğü
    var scrollbarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            scrollbarVisible = true
        } else {
            delay(1500)
            scrollbarVisible = false
        }
    }

    // Geri tuşu yönetimi
    BackHandler(enabled = viewingPersonId != null) {
        viewingPersonId = null
    }

    // Presenter Bağlantısı
    val viewImpl = remember {
        object : GalleryContract.View {
            override fun showLoading(isLoading: Boolean) { loading = isLoading }
            override fun showError(message: String) { error = message }
            override fun updateProgress(processed: Int, total: Int) { progress = processed to total }
            override fun hideProgress() { progress = null }
            override fun displayClusters(c: Map<Int, List<Photo>>, d: Map<Int, PersonDetails>) {
                clusters = c; personDetails = d
            }
            override fun showSummaryToast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            override fun showContactsMatchPrompt() { showMatchPrompt = true }
            override fun refreshAfterFaceReassigned() { Toast.makeText(context, "Kişi güncellendi", Toast.LENGTH_SHORT).show() }
            override fun showSelectPersonDialog(faceId: Long, persons: List<PersonEntity>) {
                val mapped = persons.map { p ->
                    val detail = personDetails[p.personId.toInt()]
                    val display = detail?.displayName ?: "Kişi ${p.personId}"
                    PersonChoice(p.personId, display)
                }
                selectedPersonDialogData = faceId to mapped
            }
        }
    }

    LaunchedEffect(Unit) { presenter.attach(viewImpl) }
    DisposableEffect(Unit) { onDispose { presenter.detach() } }

    // İzin İstekleri
    LaunchedEffect(storagePermissionState.hasPermission) { if (!storagePermissionState.hasPermission) storagePermissionState.launchPermissionRequest() }
    LaunchedEffect(storagePermissionState.hasPermission, contactsPermissionState.hasPermission) { if (storagePermissionState.hasPermission && !contactsPermissionState.hasPermission) contactsPermissionState.launchPermissionRequest() }

    val permissionsReady =
        storagePermissionState.hasPermission && contactsPermissionState.hasPermission

    LaunchedEffect(permissionsReady) {
        if (permissionsReady) {
            presenter.loadCachedClusters()
            presenter.syncIfNeeded()
        }
    }

    // ------------------ ANA UI ------------------
    val bg = colorResource(R.color.background)
    val primary = colorResource(R.color.primary)

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .background(bg)

        ) {
            Column(Modifier.fillMaxSize()) {
                // Progress Bar
                progress?.let { (done, total) ->
                    GalleryProgressSection(
                        done = done,
                        total = total
                    )
                }

                // Ana İçerik
                Box(Modifier.weight(1f)) {
                    when {
                        error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Hata: $error", color = MaterialTheme.colorScheme.error) }
                        loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = primary) }
                        clusters.isNotEmpty() -> {
                            if (viewingPersonId == null) {
                                // ALBÜMLER (Yuvarlak Tasarım)
                                AlbumsGrid(
                                    state = gridState,
                                    clusters = clusters,
                                    personDetails = personDetails,
                                    onPersonClick = { viewingPersonId = it }
                                )
                            } else {
                                // DETAYLAR
                                PersonDetailGrid(


                                    state = gridState,
                                    photos = clusters[viewingPersonId] ?: emptyList(),
                                    onPhotoClick = { selectedPhotoUri = it.contentUri; selectedFaceId = it.faceId }
                                )
                            }
                        }
                        else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Görüntülenecek kişi yok.") }
                    }
                }
            }

            // HEADER
            val headerTitle = if (viewingPersonId != null) {
                personDetails[viewingPersonId]?.displayName ?: "Kişi Detayı"
            } else {
                "Nomi"
            }

            CollapsingHeader(
                state = gridState,
                title = headerTitle,
                onToggleTheme = {
                    val currentMode = ThemePreferences.getThemeMode(context)
                    val newMode = ThemePreferences.toggleTheme(currentMode)
                    ThemePreferences.saveThemeMode(context, newMode)
                    AppCompatDelegate.setDefaultNightMode(newMode)
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // SCROLLBAR
            VerticalScrollbar(
                state = gridState,
                visible = scrollbarVisible,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 60.dp, bottom = 10.dp)
            )

            // DİALOGLAR
            if (selectedPhotoUri != null && selectedFaceId != null) {
                FullScreenPhotoViewer(
                    photoUri = selectedPhotoUri!!,
                    onWrongPersonClick = { presenter.onWrongPersonClicked(selectedFaceId!!) },
                    onDismiss = {
                        selectedPhotoUri = null
                        selectedFaceId = null
                    }
                )
            }

            progress?.let { (done, total) -> if (total > 0) ScanningOverlay((done * 100f / total).coerceIn(0f, 100f)) }

            MatchContactsBottomSheet(
                visible = showMatchPrompt,
                onDismiss = { showMatchPrompt = false },
                onStartClick = {
                    showMatchPrompt = false
                    presenter.matchFacesWithContacts()
                }
            )

            selectedPersonDialogData?.let { (faceId, persons) ->
                SelectPersonDialog(
                    faceId = faceId,
                    persons = persons,
                    onDismiss = { selectedPersonDialogData = null },
                    onConfirm = { selectedPersonId ->
                        presenter.onPersonSelectedForFace(faceId, selectedPersonId)
                        selectedPersonDialogData = null
                        selectedPhotoUri = null
                        selectedFaceId = null
                    }
                )
            }
        }
    }
}