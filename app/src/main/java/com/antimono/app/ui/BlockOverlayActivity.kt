package com.antimono.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.antimono.app.databinding.ActivityBlockOverlayBinding

class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockOverlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra("EXTRA_TITLE") ?: "¡Changuito al Calabozo! 🐵⛓️"
        val reason = intent.getStringExtra("EXTRA_REASON") ?: "El impulso fue neutralizado con éxito."

        binding.tvBlockTitle.text = title
        binding.tvBlockReason.text = reason

        // 1. Efecto Háptico (Vibración de bloqueo)
        triggerHapticFeedback()

        // 2. Sonido sutil de cerrojo
        playLockSound()

        // 3. Animación de rebote en el Changuito
        animateMonkeyCard()

        binding.btnUnderstood.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 120), intArrayOf(0, 180, 0, 255), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playLockSound() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateMonkeyCard() {
        binding.cardMonkey.scaleX = 0.5f
        binding.cardMonkey.scaleY = 0.5f
        binding.cardMonkey.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(binding.cardMonkey, "scaleX", 0.5f, 1.05f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(binding.cardMonkey, "scaleY", 0.5f, 1.05f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(binding.cardMonkey, "alpha", 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 600
            interpolator = OvershootInterpolator(1.8f)
            start()
        }
    }
}
