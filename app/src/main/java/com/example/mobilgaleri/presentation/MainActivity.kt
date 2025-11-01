// app/src/main/java/com/example/mobilgaleri/presentation/MainActivity.kt
package com.example.mobilgaleri.presentation

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {

    private var contentSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Sistem barlarını açık gri yap ve koyu ikon/metin kullan
        window.statusBarColor = Color.parseColor("#F3F4F6")
        window.navigationBarColor = Color.parseColor("#F3F4F6")
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

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
        setContent { AppRoot() }
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

        val presenter = remember {
            GalleryPresenter(
                galleryRepository = galleryRepo,
                faceRepository = faceRepo,
                personRepository = personRepo,
                contactsRepository = contactsRepo,
                contentResolver = cr,
                detector = detector,
                embedder = embedder,
                appContext = ctx
            )
        }

        // UI artık GalleryScreen içinde yönetiliyor (Scaffold/topbar dahil)
        Box(Modifier.fillMaxSize().padding(0.dp)) {
            GalleryScreen(presenter)
        }
    }
}
