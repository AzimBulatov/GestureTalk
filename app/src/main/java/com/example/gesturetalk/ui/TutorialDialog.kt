package com.example.gesturetalk.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.gesturetalk.R
import com.example.gesturetalk.databinding.DialogTutorialBinding
import com.example.gesturetalk.databinding.ItemTutorialSlideBinding

/**
 * Dialog с инструкцией по использованию распознавания жестов
 */
class TutorialDialog(private val activity: Activity) {
    
    data class TutorialSlide(
        val icon: String,
        val title: String,
        val description: String
    )
    
    private val slides = listOf(
        TutorialSlide(
            "🤖",
            "Как работает модель",
            "Модель анализирует движения ваших рук в реальном времени и распознает жесты жестового языка"
        ),
        TutorialSlide(
            "👋",
            "Начало демонстрации",
            "Поднимите руку или обе руки в кадр, чтобы начать демонстрацию жеста. Убедитесь, что руки полностью видны"
        ),
        TutorialSlide(
            "✋",
            "Показ жеста",
            "Покажите жест четко и держите его несколько секунд. Модель будет собирать кадры для анализа"
        ),
        TutorialSlide(
            "⬇️",
            "Завершение демонстрации",
            "Опустите руку или обе руки вниз, чтобы завершить демонстрацию жеста"
        ),
        TutorialSlide(
            "▶️",
            "Кнопка \"Старт\"",
            "Нажмите кнопку \"Старт\", чтобы начать процесс распознавания. После этого покажите жест, и модель обработает собранные кадры"
        ),
        TutorialSlide(
            "🗑️",
            "Кнопка \"Очистить\"",
            "Нажмите \"Очистить\", чтобы удалить историю распознанных слов и начать заново"
        )
    )
    
    private lateinit var binding: DialogTutorialBinding
    private lateinit var dialog: AlertDialog
    private var currentPage = 0
    
    fun show() {
        binding = DialogTutorialBinding.inflate(LayoutInflater.from(activity))
        
        // Настраиваем ViewPager
        val adapter = TutorialPagerAdapter(slides)
        binding.viewPager.adapter = adapter
        
        // Настраиваем индикатор точек
        setupDotsIndicator(slides.size)
        updateDotsIndicator(0)
        updateButtons(0)
        
        // Обработчик изменения страницы
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateDotsIndicator(position)
                updateButtons(position)
            }
        })
        
        // Обработчик кнопки "Назад"
        binding.btnPrevious.setOnClickListener {
            if (currentPage > 0) {
                binding.viewPager.currentItem = currentPage - 1
            }
        }
        
        // Обработчик кнопки "Далее"
        binding.btnNext.setOnClickListener {
            if (currentPage < slides.size - 1) {
                binding.viewPager.currentItem = currentPage + 1
            }
        }
        
        // Обработчик кнопки "Приступим!"
        binding.btnStart.setOnClickListener {
            dialog.dismiss()
        }
        
        // Создаем и показываем dialog
        dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun updateButtons(position: Int) {
        // Показываем/скрываем кнопку "Назад"
        binding.btnPrevious.visibility = if (position > 0) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        
        // На последнем слайде показываем "Приступим!", на остальных "Далее"
        if (position == slides.size - 1) {
            binding.btnNext.visibility = android.view.View.GONE
            binding.btnStart.visibility = android.view.View.VISIBLE
        } else {
            binding.btnNext.visibility = android.view.View.VISIBLE
            binding.btnStart.visibility = android.view.View.GONE
        }
    }
    
    private fun setupDotsIndicator(count: Int) {
        binding.dotsIndicator.removeAllViews()
        
        val dots = Array(count) { View(activity) }
        
        dots.forEach { dot ->
            val params = LinearLayout.LayoutParams(
                8.dpToPx(),  // Уменьшили с 16 до 8
                8.dpToPx()   // Уменьшили с 16 до 8
            ).apply {
                setMargins(4.dpToPx(), 0, 4.dpToPx(), 0)  // Уменьшили отступы с 8 до 4
            }
            
            dot.layoutParams = params
            dot.background = createDotDrawable(false)
            binding.dotsIndicator.addView(dot)
        }
    }
    
    private fun updateDotsIndicator(position: Int) {
        for (i in 0 until binding.dotsIndicator.childCount) {
            val dot = binding.dotsIndicator.getChildAt(i)
            dot.background = createDotDrawable(i == position)
        }
    }
    
    private fun createDotDrawable(isActive: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isActive) {
                activity.getColor(R.color.accent_success)
            } else {
                Color.parseColor("#E0E0E0")
            })
        }
    }
    
    private fun Int.dpToPx(): Int {
        return (this * activity.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Adapter для ViewPager2
     */
    private class TutorialPagerAdapter(
        private val slides: List<TutorialSlide>
    ) : RecyclerView.Adapter<TutorialPagerAdapter.SlideViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val binding = ItemTutorialSlideBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return SlideViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            holder.bind(slides[position])
        }
        
        override fun getItemCount(): Int = slides.size
        
        class SlideViewHolder(
            private val binding: ItemTutorialSlideBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(slide: TutorialSlide) {
                binding.tvIcon.text = slide.icon
                binding.tvSlideTitle.text = slide.title
                binding.tvSlideDescription.text = slide.description
            }
        }
    }
}
