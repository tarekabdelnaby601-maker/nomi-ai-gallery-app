// app/src/main/java/com/example/mobilgaleri/presentation/MainActivity.kt
package com.example.mobilgaleri.presentation

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.mobilgaleri.di.AppModule
import com.example.mobilgaleri.presentation.gallery.GalleryPresenter
import com.example.mobilgaleri.presentation.gallery.GalleryScreen
import com.example.mobilgaleri.presentation.onboarding.OnboardingActivity
import com.example.mobilgaleri.util.Permissions
import com.example.mobilgaleri.util.Prefs
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle

class MainActivity : AppCompatActivity() {
    private var shouldShowCustomSplash = true
    private var contentSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        shouldShowCustomSplash = savedInstanceState == null
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Sistem barlarını açık gri yap ve koyu ikon/metin kullan


        if (needsOnboarding()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            return
        }
        setupAppContentIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (!needsOnboarding()) {
            if (!Prefs.isOnboardingDone(this)) Prefs.setOnboardingDone(this, true)
            setupAppContentIfNeeded()
        }
    }

    private fun needsOnboarding(): Boolean {
        val needPhotos = !Permissions.hasPhotosPermission(this)
        val needContacts = !Permissions.hasContactsPermission(this)
        val done = Prefs.isOnboardingDone(this)
        return (needPhotos || needContacts) && !done
    }

    private fun setupAppContentIfNeeded() {
        if (contentSet) return
        contentSet = true

        setContent {
            if (shouldShowCustomSplash) {
                SplashThenApp(
                    onSplashFinished = { shouldShowCustomSplash = false }
                )
            } else {
                AppRoot()
            }
        }
    }

    @Composable
    fun AppRoot() {
        val ctx = this@MainActivity as Activity

        val cr = remember { AppModule.provideContentResolver(ctx) }
        val appDatabase = remember { AppModule.provideDatabase(ctx) }
        val faceDao = remember { AppModule.provideFaceDao(appDatabase) }
        val personDao = remember { AppModule.providePersonDao(appDatabase) }

        val galleryRepo = remember { AppModule.provideGalleryRepository(cr) }
        val faceRepo = remember { AppModule.provideFaceRepository(faceDao) }
        val personRepo = remember { AppModule.providePersonRepository(personDao) }
        val contactsRepo = remember { AppModule.provideContactsRepository(cr) }

        val detector = remember { AppModule.provideFaceDetector(ctx) }
        val embedder = remember { AppModule.provideEmbedder(ctx) }

        val scanCoordinator = remember { AppModule.provideScanCoordinator(ctx) }

        val presenter = remember {
            GalleryPresenter(
                galleryRepository = galleryRepo,
                faceRepository = faceRepo,
                personRepository = personRepo,
                contactsRepository = contactsRepo,
                contentResolver = cr,
                detector = detector,
                embedder = embedder,
                appContext = ctx,
                scanCoordinator = scanCoordinator
            )
        }

        // 🔹 BÜTÜN İÇERİK ARTIK NOMİ ARKAPLAN ÜZERİNDE
        GalleryScreen(presenter)
    }

    @Composable
    fun SplashThenApp(onSplashFinished: () -> Unit) {
        var showSplash = remember { mutableStateOf(true) }

        if (showSplash.value) {
            CustomSplash(
                onFinished = {
                    showSplash.value = false
                    onSplashFinished()
                }
            )
        } else {
            AppRoot()
        }
    }

    @Composable
    fun CustomSplash(onFinished: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF020C1F)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Yüz/logon olan resim
                Image(
                    painter = painterResource(id = com.example.mobilgaleri.R.drawable.ic_logo_splash),
                    contentDescription = "NOMİ Logo",
                    modifier = Modifier.size(200.dp)
                )

                Spacer(Modifier.height(24.dp))

                // SADECE SLOGAN, TIRNAK İÇİNDE VE İTALİK
                Text(
                    text = "\"Her yüzün bir ismi var.\"",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }

        // 1.5 sn sonra ana ekrana geç (15 sn gereksiz uzun, istersen değiştir)
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(150)
            onFinished()
        }
    }
}

