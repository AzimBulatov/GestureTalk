package com.example.gesturetalk.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.gesturetalk.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Плавные переходы между экранами (fade, без сдвига).
 */
object NavTransitions {

    const val EXTRA_PREVIOUS_NAV_ITEM = "extra_previous_nav_item"
    const val EXTRA_TARGET_NAV_ITEM = "extra_target_nav_item"

    fun AppCompatActivity.openBottomNavTab(
        target: Class<*>,
        targetItemId: Int,
        currentItemId: Int
    ) {
        if (target.isInstance(this)) return
        startActivity(
            Intent(this, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_PREVIOUS_NAV_ITEM, currentItemId)
                putExtra(EXTRA_TARGET_NAV_ITEM, targetItemId)
            }
        )
        overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
    }

    fun BottomNavigationView.animateSelectionFromIntent(
        intent: Intent,
        defaultItemId: Int,
        onReady: () -> Unit
    ) {
        val targetId = intent.getIntExtra(EXTRA_TARGET_NAV_ITEM, defaultItemId)
        val resolvedTarget = if (targetId != -1) targetId else defaultItemId

        // Avoid forced indicator animation between activities.
        // It was causing visible stutter on some devices.
        setOnItemSelectedListener(null)
        selectedItemId = resolvedTarget
        onReady()
    }

    fun AppCompatActivity.clearNavTransitionExtras() {
        intent.removeExtra(EXTRA_PREVIOUS_NAV_ITEM)
        intent.removeExtra(EXTRA_TARGET_NAV_ITEM)
    }

    fun AppCompatActivity.setupGestureTalkBottomNav(
        bottomNav: BottomNavigationView,
        defaultItemId: Int,
        attachListener: (BottomNavigationView) -> Unit
    ) {
        bottomNav.applyGestureTalkNavStyle()
        bottomNav.animateSelectionFromIntent(intent, defaultItemId) {
            attachListener(bottomNav)
        }
        clearNavTransitionExtras()
    }

    fun AppCompatActivity.navigateWithFade(intent: Intent, finishCurrent: Boolean = false) {
        startActivity(intent)
        overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
        if (finishCurrent) {
            finish()
        }
    }

    fun AppCompatActivity.finishWithFade() {
        finish()
        overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
    }
}
