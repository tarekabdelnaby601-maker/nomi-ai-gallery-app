package com.example.mobilgaleri.util

import android.content.Context
import androidx.core.content.edit

object Prefs {
    private const val FILE = "onboarding_prefs"
    private const val KEY_DONE = "onboarding_done"

    fun isOnboardingDone(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun setOnboardingDone(ctx: Context, done: Boolean) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY_DONE, done) }
}