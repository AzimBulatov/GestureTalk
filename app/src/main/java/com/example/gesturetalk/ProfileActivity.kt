package com.example.gesturetalk

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gesturetalk.databinding.ActivityProfileBinding
import com.example.gesturetalk.firebase.FirebaseAuthService
import com.example.gesturetalk.ui.NavTransitions.navigateWithFade
import com.example.gesturetalk.ui.NavTransitions.openBottomNavTab
import com.example.gesturetalk.ui.NavTransitions.setupGestureTalkBottomNav
import com.example.gesturetalk.utils.UserProfileCache
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val firebaseService = FirebaseAuthService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)

        setupBottomNav()
        setupListeners()
        loadUserProfile()
    }

    private fun setupBottomNav() {
        setupGestureTalkBottomNav(binding.bottomNav, R.id.nav_profile) { nav ->
            nav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_profile -> true
                    R.id.nav_ai -> {
                        openBottomNavTab(AskAiActivity::class.java, R.id.nav_ai, R.id.nav_profile)
                        true
                    }
                    R.id.nav_recognition -> {
                        openBottomNavTab(MainActivity::class.java, R.id.nav_recognition, R.id.nav_profile)
                        true
                    }
                    R.id.nav_stats -> {
                        openBottomNavTab(StatsActivity::class.java, R.id.nav_stats, R.id.nav_profile)
                        true
                    }
                    R.id.nav_guide_online -> {
                        openBottomNavTab(GuideOnlineActivity::class.java, R.id.nav_guide_online, R.id.nav_profile)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSettings.setOnClickListener {
            navigateWithFade(Intent(this, SettingsActivity::class.java))
        }

        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)

        val etLastName = dialogView.findViewById<TextInputEditText>(R.id.etLastName)
        val etFirstName = dialogView.findViewById<TextInputEditText>(R.id.etFirstName)
        val etMiddleName = dialogView.findViewById<TextInputEditText>(R.id.etMiddleName)
        val etBirthDate = dialogView.findViewById<TextInputEditText>(R.id.etBirthDate)
        val tilBirthDate = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilBirthDate)
        val etLearningStatus = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.etLearningStatus)

        etLastName.setText(binding.tvLastName.text.toString().takeIf { it != "Не заполнено" } ?: "")
        etFirstName.setText(binding.tvFirstName.text.toString().takeIf { it != "Не заполнено" } ?: "")
        etMiddleName.setText(binding.tvMiddleName.text.toString().takeIf { it != "Не заполнено" } ?: "")
        etBirthDate.setText(binding.tvBirthDate.text.toString().takeIf { it != "Не указана" } ?: "")

        tilBirthDate.setEndIconOnClickListener {
            showDatePicker { selectedDate ->
                etBirthDate.setText(selectedDate)
            }
        }

        etBirthDate.setOnClickListener {
            showDatePicker { selectedDate ->
                etBirthDate.setText(selectedDate)
            }
        }

        val statuses = arrayOf("Начинающий", "Средний", "Продвинутый", "Эксперт")
        val adapter = ArrayAdapter(this, R.layout.dropdown_item, statuses)
        etLearningStatus.setAdapter(adapter)
        etLearningStatus.setText(binding.tvLearningStatus.text.toString(), false)
        etLearningStatus.dropDownHeight = android.widget.ListPopupWindow.WRAP_CONTENT
        etLearningStatus.dropDownVerticalOffset = 0
        etLearningStatus.setOnTouchListener { _, _ ->
            etLearningStatus.showDropDown()
            false
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).setOnClickListener {
            val lastName = etLastName.text.toString().trim()
            val firstName = etFirstName.text.toString().trim()
            val middleName = etMiddleName.text.toString().trim()
            val birthDate = etBirthDate.text.toString().trim()
            val learningStatus = etLearningStatus.text.toString().trim()

            val fullName = listOf(lastName, firstName, middleName)
                .filter { it.isNotEmpty() }
                .joinToString(" ")

            val currentUser = firebaseService.getCurrentUser()
            if (currentUser == null) {
                Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val updates = mutableMapOf<String, Any>()
                    updates["fullName"] = fullName
                    updates["birthDate"] = birthDate
                    updates["learningStatus"] = learningStatus.ifEmpty { "Начинающий" }

                    firebaseService.updateUserProfile(updates)
                        .onSuccess {
                            val profile = UserProfileCache.Profile(
                                fullName = fullName,
                                birthDate = birthDate,
                                learningStatus = learningStatus.ifEmpty { "Начинающий" },
                                email = currentUser.email ?: ""
                            )
                            UserProfileCache.save(this@ProfileActivity, currentUser.uid, profile)
                            displayProfile(profile, currentUser.email)

                            Toast.makeText(this@ProfileActivity, "Профиль обновлен", Toast.LENGTH_SHORT).show()

                            com.example.gesturetalk.utils.AchievementsManager.checkProfileComplete(
                                this@ProfileActivity,
                                fullName,
                                currentUser.email,
                                birthDate.takeIf { it.isNotEmpty() }
                            )

                            dialog.dismiss()
                        }
                        .onFailure { error ->
                            android.util.Log.e("ProfileActivity", "Ошибка обновления профиля", error)

                            val errorMessage = when {
                                error.message?.contains("Permission denied") == true ->
                                    "❌ БАЗА ДАННЫХ НЕ НАСТРОЕНА\n\n" +
                                        "Откройте Firebase Console:\n" +
                                        "console.firebase.google.com\n\n" +
                                        "1. Realtime Database → Rules\n" +
                                        "2. Вставьте:\n\n" +
                                        "{\n" +
                                        "  \"rules\": {\n" +
                                        "    \"users\": {\n" +
                                        "      \"\$uid\": {\n" +
                                        "        \".read\": \"auth != null && auth.uid == \$uid\",\n" +
                                        "        \".write\": \"auth != null && auth.uid == \$uid\"\n" +
                                        "      }\n" +
                                        "    }\n" +
                                        "  }\n" +
                                        "}\n\n" +
                                        "3. Нажмите PUBLISH"
                                else -> "Ошибка: ${error.message}"
                            }

                            AlertDialog.Builder(this@ProfileActivity)
                                .setTitle("Ошибка сохранения")
                                .setMessage(errorMessage)
                                .setPositiveButton("Понятно", null)
                                .show()
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileActivity", "Исключение при сохранении", e)
                    Toast.makeText(this@ProfileActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = java.util.Calendar.getInstance()

        val builder = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
        builder.setTitleText("Выберите дату рождения")
        builder.setSelection(calendar.timeInMillis)

        val datePicker = builder.build()

        datePicker.addOnPositiveButtonClickListener { selection: Long ->
            val selectedCalendar = java.util.Calendar.getInstance()
            selectedCalendar.timeInMillis = selection

            val day = selectedCalendar.get(java.util.Calendar.DAY_OF_MONTH)
            val month = selectedCalendar.get(java.util.Calendar.MONTH) + 1
            val year = selectedCalendar.get(java.util.Calendar.YEAR)

            val formattedDate = String.format("%02d.%02d.%04d", day, month, year)
            onDateSelected(formattedDate)
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun loadUserProfile() {
        val currentUser = firebaseService.getCurrentUser()
        if (currentUser == null) {
            navigateWithFade(Intent(this, AuthActivity::class.java), finishCurrent = true)
            return
        }

        binding.tvEmail.text = currentUser.email ?: "Не указан"

        val cached = UserProfileCache.get(this, currentUser.uid)
        if (cached != null) {
            displayProfile(cached, currentUser.email)
            return
        }

        lifecycleScope.launch {
            UserProfileCache.syncFromRemote(this@ProfileActivity, firebaseService, currentUser)
                .onSuccess { profile -> displayProfile(profile, currentUser.email) }
                .onFailure { error ->
                    android.util.Log.w("ProfileActivity", "Не удалось загрузить профиль: ${error.message}")
                    displayProfile(UserProfileCache.Profile(email = currentUser.email ?: ""), currentUser.email)
                }
        }
    }

    private fun displayProfile(profile: UserProfileCache.Profile, authEmail: String?) {
        val fullName = profile.fullName
        val nameParts = fullName.split(" ").filter { it.isNotEmpty() }

        binding.tvLastName.text = nameParts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "Не заполнено"
        binding.tvFirstName.text = nameParts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: "Не заполнено"
        binding.tvMiddleName.text = nameParts.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "Не заполнено"

        val birthDate = profile.birthDate.takeIf { it.isNotEmpty() } ?: "Не указана"
        binding.tvBirthDate.text = birthDate
        binding.tvLearningStatus.text = profile.learningStatus

        val email = profile.email.ifEmpty { authEmail ?: "" }
        if (email.isNotEmpty()) {
            binding.tvEmail.text = email
        }

        com.example.gesturetalk.utils.AchievementsManager.checkProfileComplete(
            this,
            fullName,
            authEmail,
            profile.birthDate.takeIf { it.isNotEmpty() }
        )
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_profile
    }
}
