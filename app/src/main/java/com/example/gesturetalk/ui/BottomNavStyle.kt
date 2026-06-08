package com.example.gesturetalk.ui

import com.example.gesturetalk.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Круглый индикатор фиксированного размера (не растягивается по ширине вкладки).
 */
fun BottomNavigationView.applyGestureTalkNavStyle() {
    // Отключаем программное управление ActiveIndicator — фон берётся из itemBackground selector.
    isItemActiveIndicatorEnabled = false
}
