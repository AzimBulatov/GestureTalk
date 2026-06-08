package com.example.gesturetalk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.gesturetalk.databinding.ActivityGuideDetailBinding
import com.example.gesturetalk.guide.RslGuideRepository
import com.example.gesturetalk.ui.NavTransitions.finishWithFade

class GuideDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Отслеживаем открытие справочника
        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)
        com.example.gesturetalk.utils.AchievementsManager.incrementGuideOpens(this)

        binding.btnBack.setOnClickListener { finishWithFade() }

        val word = intent.getStringExtra(EXTRA_WORD).orEmpty()
        val entry = RslGuideRepository.findByWord(word)

        binding.tvWord.text = entry?.word ?: word
        binding.tvDescription.text = entry?.description ?: "Описание пока не добавлено."
    }

    companion object {
        const val EXTRA_WORD = "extra_word"
    }
}
