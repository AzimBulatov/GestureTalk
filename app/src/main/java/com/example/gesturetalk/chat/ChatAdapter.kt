package com.example.gesturetalk.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gesturetalk.R

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userMessageLayout: LinearLayout = view.findViewById(R.id.userMessageLayout)
        val aiMessageLayout: LinearLayout = view.findViewById(R.id.aiMessageLayout)
        val tvUserMessage: TextView = view.findViewById(R.id.tvUserMessage)
        val tvAiMessage: TextView = view.findViewById(R.id.tvAiMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        if (message.isUser) {
            holder.userMessageLayout.visibility = View.VISIBLE
            holder.aiMessageLayout.visibility = View.GONE
            holder.tvUserMessage.text = message.text
        } else {
            holder.userMessageLayout.visibility = View.GONE
            holder.aiMessageLayout.visibility = View.VISIBLE
            holder.tvAiMessage.text = message.text
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}
