package com.mindtype.mobile.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mindtype.mobile.databinding.ActivitySettingsBinding
import com.mindtype.mobile.export.DataExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("mindtype_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "—") ?: "—"
        binding.tvCurrentUserId.text = "Participant ID: $userId"

        binding.btnExportData.setOnClickListener {
            lifecycleScope.launch {
                binding.btnExportData.isEnabled = false
                binding.btnExportData.text = "Exporting…"
                val success = withContext(Dispatchers.IO) {
                    DataExporter(applicationContext).exportCsv()
                }
                binding.btnExportData.isEnabled = true
                binding.btnExportData.text = "Export Data"
                if (!success) {
                    Toast.makeText(this@SettingsActivity, "Export failed — no data yet", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.tvPrivacyNote.text = "Privacy Reminder:\n• No typed text is stored\n• Data never leaves your device\n• Uninstall the app to delete all data"
    }
}
