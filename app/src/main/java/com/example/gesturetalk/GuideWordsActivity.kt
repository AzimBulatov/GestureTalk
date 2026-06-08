package com.example.gesturetalk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gesturetalk.databinding.ActivityGuideWordsBinding
import com.example.gesturetalk.guide.GuideWordsAdapter
import com.example.gesturetalk.guide.RslGuideRepository
import com.example.gesturetalk.ui.NavTransitions.finishWithFade
import com.example.gesturetalk.ui.NavTransitions.navigateWithFade

class GuideWordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideWordsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideWordsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Отслеживаем открытие справочника
        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)
        com.example.gesturetalk.utils.AchievementsManager.incrementGuideOpens(this)

        val letter = intent.getStringExtra(EXTRA_LETTER)?.firstOrNull()?.uppercaseChar() ?: 'А'
        binding.tvTitle.text = "Буква $letter"
        binding.btnBack.setOnClickListener { finishWithFade() }

        val words = RslGuideRepository.wordsByLetter(letter)
        binding.rvWords.layoutManager = LinearLayoutManager(this)
        binding.rvWords.adapter = GuideWordsAdapter(words) { entry ->
            val intent = Intent(this, GuideDetailActivity::class.java)
            intent.putExtra(GuideDetailActivity.EXTRA_WORD, entry.word)
            navigateWithFade(intent)
        }
    }

    companion object {
        const val EXTRA_LETTER = "extra_letter"
    }
}
