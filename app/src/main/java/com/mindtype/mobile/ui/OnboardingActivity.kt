package com.mindtype.mobile.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mindtype.mobile.R
import com.mindtype.mobile.data.AppDatabase
import com.mindtype.mobile.data.entity.SessionEntity
import com.mindtype.mobile.data.entity.UserEntity
import com.mindtype.mobile.databinding.ActivityOnboardingBinding
import com.mindtype.mobile.workers.StressLabelWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var currentStep = 0
    private val totalSteps = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already set up, go directly to main dashboard
        val prefs = getSharedPreferences("mindtype_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_complete", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showStep(0)

        binding.btnNext.setOnClickListener { handleNextStep() }
        binding.btnBack.setOnClickListener { if (currentStep > 0) showStep(currentStep - 1) }
    }

    private fun showStep(step: Int) {
        currentStep = step
        binding.progressBar.progress = ((step + 1) * 100) / totalSteps

        when (step) {
            0 -> {
                binding.tvTitle.text = "Welcome to MindType 🧠"
                binding.tvDescription.text =
                    "MindType passively monitors your mental stress level by analyzing HOW you type — not WHAT you type.\n\nTyping speed, key timing, and touch pressure reveal your stress state in real time."
                binding.btnBack.isEnabled = false
                binding.btnNext.text = "Let's Start →"
            }
            1 -> {
                binding.tvTitle.text = "🔒 Your Privacy is Protected"
                binding.tvDescription.text =
                    "✅ NO text content is ever recorded\n✅ Only timing metadata (milliseconds) is captured\n✅ All data stays on your phone — never transmitted\n✅ You can delete all data by uninstalling the app\n\nWe are a VIT-AP University research team. This data is used exclusively for academic purposes."
                binding.btnBack.isEnabled = true
                binding.btnNext.text = "I Understand →"
            }
            2 -> {
                binding.tvTitle.text = "Enter Your Participant ID"
                binding.tvDescription.text = "Your researcher has assigned you an ID (e.g., U01, U02).\nEnter it below:"
                binding.etUserId.visibility = android.view.View.VISIBLE
                binding.btnNext.text = "Continue →"
            }
            3 -> {
                binding.etUserId.visibility = android.view.View.GONE
                binding.tvTitle.text = "Set Up the MindType Keyboard"
                binding.tvDescription.text =
                    "Follow these steps:\n\n1. Tap the button below to open Settings\n2. Go to  Language & Input\n3. Tap 'Manage Keyboards' or 'On-screen Keyboard'\n4. Toggle MindType → ON\n5. Set MindType as your Default keyboard\n6. Come back to this screen"
                binding.btnOpenSettings.visibility = android.view.View.VISIBLE
                binding.btnOpenSettings.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
                binding.btnNext.text = "I've Done This →"
            }
            4 -> {
                binding.btnOpenSettings.visibility = android.view.View.GONE
                val imeEnabled = isMindTypeEnabled()
                if (!imeEnabled) {
                    binding.tvTitle.text = "⚠️ Keyboard Not Detected Yet"
                    binding.tvDescription.text =
                        "MindType keyboard is not set as default yet.\n\nPlease go back and follow the setup steps."
                    binding.btnNext.text = "Check Again"
                } else {
                    binding.tvTitle.text = "🎉 You're All Set!"
                    binding.tvDescription.text =
                        "MindType is active and collecting data in the background.\n\nJust use your phone normally — type in WhatsApp, browsers, notes, anywhere.\n\nYou'll receive a quick stress check-in every 10 minutes."
                    binding.btnNext.text = "Open Dashboard →"
                    completeOnboarding()
                }
            }
        }
    }

    private fun handleNextStep() {
        if (currentStep == 2) {
            val uid = binding.etUserId.text.toString().trim()
            if (uid.isEmpty()) {
                Toast.makeText(this, "Please enter your User ID", Toast.LENGTH_SHORT).show()
                return
            }
            saveUserAndSession(uid)
        }
        if (currentStep < totalSteps - 1) {
            showStep(currentStep + 1)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun saveUserAndSession(userId: String) {
        val db = AppDatabase.getInstance(applicationContext)
        val sessionId = UUID.randomUUID().toString()
        val prefs = getSharedPreferences("mindtype_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("user_id", userId)
            .putString("current_session_id", sessionId)
            .apply()

        CoroutineScope(Dispatchers.IO).launch {
            db.userDao().insert(UserEntity(userId))
            db.sessionDao().insert(SessionEntity(sessionId = sessionId, userId = userId))
        }
    }

    private fun completeOnboarding() {
        getSharedPreferences("mindtype_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_complete", true).apply()
        StressLabelWorker.schedule(this)
    }

    private fun isMindTypeEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any {
            it.packageName == packageName
        }
    }
}
