package com.example.gesturetalk.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gesturetalk.databinding.ItemGuideLetterBinding

class LettersAdapter(
    private val letters: List<Char>,
    private val onClick: (Char) -> Unit
) : RecyclerView.Adapter<LettersAdapter.LetterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LetterViewHolder {
        val binding = ItemGuideLetterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LetterViewHolder(binding)
    }

    override fun getItemCount(): Int = letters.size

    override fun onBindViewHolder(holder: LetterViewHolder, position: Int) {
        holder.bind(letters[position])
    }

    inner class LetterViewHolder(
        private val binding: ItemGuideLetterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(letter: Char) {
            binding.tvLetter.text = letter.toString()
            binding.root.setOnClickListener { onClick(letter) }
        }
    }
}
