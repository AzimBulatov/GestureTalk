package com.example.gesturetalk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.gesturetalk.databinding.ActivityGuideBinding
import com.example.gesturetalk.guide.LettersAdapter
import com.example.gesturetalk.guide.RslGuideRepository
import com.example.gesturetalk.ui.NavTransitions.navigateWithFade
import com.example.gesturetalk.ui.NavTransitions.openBottomNavTab
import com.example.gesturetalk.ui.NavTransitions.setupGestureTalkBottomNav

class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Отслеживаем открытие справочника
        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)
        com.example.gesturetalk.utils.AchievementsManager.incrementGuideOpens(this)
        
        setupLetters()
        setupBottomNav()
    }

    private fun setupLetters() {
        val letters = RslGuideRepository.letters()
        binding.rvLetters.layoutManager = GridLayoutManager(this, 6)
        binding.rvLetters.adapter = LettersAdapter(letters) { letter ->
            val intent = Intent(this, GuideWordsActivity::class.java)
            intent.putExtra(GuideWordsActivity.EXTRA_LETTER, letter.toString())
            navigateWithFade(intent)
        }
    }

    private fun setupBottomNav() {
        setupGestureTalkBottomNav(binding.bottomNav, R.id.nav_guide_online) { nav ->
            nav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_guide_online -> {
                        openBottomNavTab(GuideOnlineActivity::class.java, R.id.nav_guide_online, R.id.nav_guide_online)
                        true
                    }
                    R.id.nav_ai -> {
                        openBottomNavTab(AskAiActivity::class.java, R.id.nav_ai, R.id.nav_guide_online)
                        true
                    }
                    R.id.nav_recognition -> {
                        openBottomNavTab(MainActivity::class.java, R.id.nav_recognition, R.id.nav_guide_online)
                        true
                    }
                    R.id.nav_stats -> {
                        openBottomNavTab(StatsActivity::class.java, R.id.nav_stats, R.id.nav_guide_online)
                        true
                    }
                    R.id.nav_profile -> {
                        openBottomNavTab(ProfileActivity::class.java, R.id.nav_profile, R.id.nav_guide_online)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_guide_online
    }
}

