package com.example.gesturetalk.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gesturetalk.R

data class ChatHistory(
    val id: String = "",
    val title: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf()
) {
    // Пустой конструктор для Firebase
    constructor() : this("", "", 0L, mutableListOf())
}

class ChatHistoryAdapter(
    private val chats: MutableList<ChatHistory>,
    private val onClick: (ChatHistory) -> Unit,
    private val onLongClick: (ChatHistory) -> Boolean
) : RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvChatTitle: TextView = view.findViewById(R.id.tvChatTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = chats[position]
        holder.tvChatTitle.text = chat.title
        holder.itemView.setOnClickListener {
            onClick(chat)
        }
        holder.itemView.setOnLongClickListener {
            onLongClick(chat)
        }
    }

    override fun getItemCount() = chats.size

    fun addChat(chat: ChatHistory) {
        chats.add(0, chat)
        notifyItemInserted(0)
    }
}
