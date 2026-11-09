package com.antimono.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.antimono.app.databinding.ActivityBlockOverlayBinding

class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockOverlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reason = intent.getStringExtra("EXTRA_REASON") ?: "Sitio bloqueado por AntiMono"
        binding.tvBlockReason.text = reason

        binding.btnUnderstood.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }
}
