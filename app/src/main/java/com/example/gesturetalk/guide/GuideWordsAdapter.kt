package com.example.gesturetalk.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gesturetalk.databinding.ItemGuideWordBinding

class GuideWordsAdapter(
    private val words: List<RslGuideEntry>,
    private val onClick: (RslGuideEntry) -> Unit
) : RecyclerView.Adapter<GuideWordsAdapter.WordViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val binding = ItemGuideWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WordViewHolder(binding)
    }

    override fun getItemCount(): Int = words.size

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        holder.bind(words[position])
    }

    inner class WordViewHolder(
        private val binding: ItemGuideWordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: RslGuideEntry) {
            binding.tvWord.text = entry.word
            binding.tvHint.text = "Подробная инструкция РЖЯ"
            binding.root.setOnClickListener { onClick(entry) }
        }
    }
}
