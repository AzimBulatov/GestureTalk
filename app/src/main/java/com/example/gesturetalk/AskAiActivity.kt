package com.example.gesturetalk

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gesturetalk.chat.*
import com.example.gesturetalk.databinding.ActivityAskAiBinding
import com.example.gesturetalk.ui.NavTransitions.openBottomNavTab
import com.example.gesturetalk.ui.NavTransitions.setupGestureTalkBottomNav
import com.example.gesturetalk.firebase.FirebaseAuthService
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.util.UUID

class AskAiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAskAiBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var historyAdapter: ChatHistoryAdapter
    private val messages = mutableListOf<ChatMessage>()
    private val chatHistory = mutableListOf<ChatHistory>()
    private var currentChatId: String? = null
    private lateinit var gigaChatService: GigaChatService
    private val firebaseService = FirebaseAuthService()
    private val database = FirebaseDatabase.getInstance().reference

    companion object {
        private const val GIGACHAT_API_KEY = "MDE5ZGY5MGEtYTgwNC03NzFmLWI2M2UtODM4ZjUyMjMwYWZkOmZiZjMwN2IyLTliYTEtNGZmNi04NmQyLTE3MjE0MjFjZDMzYQ=="
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAskAiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Обновляем серию дней
        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)

        gigaChatService = GigaChatService(GIGACHAT_API_KEY)
        setupRecyclerView()
        setupHistoryRecyclerView()
        setupInputField()
        setupBottomNav()
        setupDrawer()
        setupExampleCards()
        loadChatHistory()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@AskAiActivity)
            adapter = chatAdapter
        }
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = ChatHistoryAdapter(
            chatHistory,
            onClick = { chat ->
                loadChat(chat)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onLongClick = { chat ->
                // Показываем диалог подтверждения удаления
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Удалить чат?")
                    .setMessage("Чат \"${chat.title}\" будет удален безвозвратно")
                    .setPositiveButton("Удалить") { _, _ ->
                        deleteChat(chat)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
                true
            }
        )
        binding.rvChatHistory.apply {
            layoutManager = LinearLayoutManager(this@AskAiActivity)
            adapter = historyAdapter
        }
    }

    private fun setupInputField() {
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }
    }

    private fun setupDrawer() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.btnNewChat.setOnClickListener {
            createNewChat()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun setupExampleCards() {
        binding.cardExample1.setOnClickListener {
            sendMessage("Как показать жест \"привет\"?")
        }
        binding.cardExample2.setOnClickListener {
            sendMessage("Расскажи о русском жестовом языке")
        }
        binding.cardExample3.setOnClickListener {
            sendMessage("Как выучить жестовый язык?")
        }
    }

    private fun createNewChat() {
        // Сохраняем текущий чат если есть сообщения
        if (messages.isNotEmpty() && currentChatId != null) {
            saveCurrentChat()
        }

        // Создаем новый чат
        messages.clear()
        chatAdapter.notifyDataSetChanged()
        currentChatId = null

        // Показываем приветственный экран
        binding.welcomeLayout.visibility = View.VISIBLE
        binding.rvMessages.visibility = View.GONE
    }
    
    private fun loadChatHistory() {
        val user = firebaseService.getCurrentUser() ?: return
        
        database.child("chats").child(user.uid)
            .orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    chatHistory.clear()
                    for (chatSnapshot in snapshot.children) {
                        val chat = chatSnapshot.getValue(ChatHistory::class.java)
                        if (chat != null) {
                            chatHistory.add(0, chat) // Добавляем в начало (новые сверху)
                        }
                    }
                    historyAdapter.notifyDataSetChanged()
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@AskAiActivity,
                        "Ошибка загрузки истории",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
    
    private fun saveCurrentChat() {
        val user = firebaseService.getCurrentUser() ?: return
        val chatId = currentChatId ?: return
        
        // Сохраняем чат с сообщениями как Map, чтобы сохранить isUser правильно
        val chatData = mapOf(
            "id" to chatId,
            "title" to (chatHistory.find { it.id == chatId }?.title ?: ""),
            "timestamp" to System.currentTimeMillis(),
            "messages" to messages.map { msg ->
                mapOf(
                    "text" to msg.text,
                    "isUser" to msg.isUser,
                    "timestamp" to msg.timestamp
                )
            }
        )
        
        database.child("chats").child(user.uid).child(chatId)
            .setValue(chatData)
    }
    
    private fun saveChatToFirebase(chat: ChatHistory) {
        val user = firebaseService.getCurrentUser() ?: return
        
        val chatData = mapOf(
            "id" to chat.id,
            "title" to chat.title,
            "timestamp" to chat.timestamp,
            "messages" to emptyList<Map<String, Any>>()
        )
        
        database.child("chats").child(user.uid).child(chat.id)
            .setValue(chatData)
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Ошибка сохранения чата",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
    
    private fun deleteChat(chat: ChatHistory) {
        val user = firebaseService.getCurrentUser() ?: return
        
        database.child("chats").child(user.uid).child(chat.id)
            .removeValue()
            .addOnSuccessListener {
                chatHistory.remove(chat)
                historyAdapter.notifyDataSetChanged()
                
                // Если удаляем текущий чат, создаем новый
                if (currentChatId == chat.id) {
                    createNewChat()
                }
                
                Toast.makeText(this, "Чат удален", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка удаления чата", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadChat(chat: ChatHistory) {
        currentChatId = chat.id
        messages.clear()
        
        // Загружаем сообщения из Firebase для этого чата
        val user = firebaseService.getCurrentUser()
        if (user != null) {
            database.child("chats").child(user.uid).child(chat.id).child("messages")
                .get()
                .addOnSuccessListener { snapshot ->
                    messages.clear()
                    for (msgSnapshot in snapshot.children) {
                        // Читаем поля напрямую
                        val text = msgSnapshot.child("text").getValue(String::class.java) ?: ""
                        val isUser = msgSnapshot.child("isUser").getValue(Boolean::class.java) ?: false
                        val timestamp = msgSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                        
                        android.util.Log.d("AskAiActivity", "Загружено сообщение: text=$text, isUser=$isUser")
                        
                        val message = ChatMessage(text, isUser, timestamp)
                        messages.add(message)
                    }
                    chatAdapter.notifyDataSetChanged()
                    
                    // Проверяем достижение "Летописец" после загрузки
                    com.example.gesturetalk.utils.AchievementsManager.checkChatMessagesAchievement(this@AskAiActivity, messages.size)
                    
                    if (messages.isNotEmpty()) {
                        binding.welcomeLayout.visibility = View.GONE
                        binding.rvMessages.visibility = View.VISIBLE
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Ошибка загрузки сообщений", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun sendMessage(text: String) {
        // Отслеживаем достижения AI
        com.example.gesturetalk.utils.AchievementsManager.incrementAiMessages(this, text.length)
        
        // Создаем новый чат если это первое сообщение
        if (currentChatId == null) {
            currentChatId = UUID.randomUUID().toString()
            val newChat = ChatHistory(
                id = currentChatId!!,
                title = text.take(50),
                timestamp = System.currentTimeMillis()
            )
            chatHistory.add(0, newChat)
            historyAdapter.notifyItemInserted(0)
            saveChatToFirebase(newChat)
            
            // Отслеживаем создание нового чата
            com.example.gesturetalk.utils.AchievementsManager.incrementChatsCreated(this)
        }

        // Добавляем сообщение пользователя
        val userMessage = ChatMessage(text, isUser = true)
        chatAdapter.addMessage(userMessage)
        binding.rvMessages.scrollToPosition(messages.size - 1)

        // Скрываем приветственный экран
        if (binding.welcomeLayout.visibility == View.VISIBLE) {
            binding.welcomeLayout.visibility = View.GONE
            binding.rvMessages.visibility = View.VISIBLE
        }

        // Очищаем поле ввода
        binding.etMessage.text.clear()

        // Показываем индикатор загрузки
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false

        // Отправляем запрос к GigaChat
        lifecycleScope.launch {
            try {
                val response = gigaChatService.sendMessage(text)
                
                // Добавляем ответ AI
                val aiMessage = ChatMessage(response, isUser = false)
                chatAdapter.addMessage(aiMessage)
                binding.rvMessages.scrollToPosition(messages.size - 1)
                
                // Проверяем достижение "Летописец" (20+ сообщений в чате)
                com.example.gesturetalk.utils.AchievementsManager.checkChatMessagesAchievement(this@AskAiActivity, messages.size)
                
                // Сохраняем чат в Firebase
                saveCurrentChat()
            } catch (e: Exception) {
                Toast.makeText(
                    this@AskAiActivity,
                    "Ошибка при получении ответа",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSend.isEnabled = true
            }
        }
    }

    private fun setupBottomNav() {
        setupGestureTalkBottomNav(binding.bottomNav, R.id.nav_ai) { nav ->
            nav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_guide_online -> {
                        openBottomNavTab(GuideOnlineActivity::class.java, R.id.nav_guide_online, R.id.nav_ai)
                        true
                    }
                    R.id.nav_ai -> true
                    R.id.nav_recognition -> {
                        openBottomNavTab(MainActivity::class.java, R.id.nav_recognition, R.id.nav_ai)
                        true
                    }
                    R.id.nav_stats -> {
                        openBottomNavTab(StatsActivity::class.java, R.id.nav_stats, R.id.nav_ai)
                        true
                    }
                    R.id.nav_profile -> {
                        openBottomNavTab(ProfileActivity::class.java, R.id.nav_profile, R.id.nav_ai)
                        true
                    }
                    else -> false
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_ai
    }

    override fun onPause() {
        super.onPause()
        // Сохраняем текущий чат при выходе
        if (messages.isNotEmpty() && currentChatId != null) {
            saveCurrentChat()
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
