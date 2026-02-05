package com.example.polyglotvoice

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.animation.Animation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.nl.translate.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var tvTranscript: TextView
    private lateinit var btnListen: ImageButton // Changed to ImageButton for better pulse look
    private var isListening = false
    private var isSpanishSource = true // Toggle state
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
		// Inside onCreate, after tvTranscript = findViewById(...)
		val btnClear = findViewById<Button>(R.id.btnClear)

		btnClear.setOnClickListener {
			tvTranscript.text = "" // This wipes the transcript
			Toast.makeText(this, "Transcript cleared", Toast.LENGTH_SHORT).show()
		}
		
        btnListen = findViewById(R.id.btnListen)

        checkPermissions()
        setupTTS()
        setupSpeechRecognizer()

        btnListen.setOnClickListener {
            if (!isListening) {
                isSpanishSource = !isSpanishSource // Example: Toggle language on each click
                startListening()
            } else {
                stopListening()
            }
        }
    }

    private fun setupPulseAnimation() {
        // Create a breathing/pulsing effect
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            btnListen,
            PropertyValuesHolder.ofFloat("scaleX", 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1.2f)
        ).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }

        // Change color based on language
        val pulseColor = if (isSpanishSource) Color.RED else Color.BLUE
        btnListen.backgroundTintList = ColorStateList.valueOf(pulseColor)
    }

    private fun startListening() {
        isListening = true
        setupPulseAnimation()
        pulseAnimator?.start()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Force the language based on our toggle
            val lang = if (isSpanishSource) "es-ES" else "en-US"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        }
        speechRecognizer.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        pulseAnimator?.cancel()
        btnListen.scaleX = 1f
        btnListen.scaleY = 1f
        btnListen.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
        speechRecognizer.stopListening()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                text?.let { processTranslation(it) }
                
                // CONTINUOUS LISTENING: Restart after processing
                if (isListening) startListening() 
            }

            override fun onError(error: Int) {
                // Restart if it times out to keep it "Continuous"
                if (isListening) startListening()
            }

            // Unused required methods
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
    }

    private fun processTranslation(text: String) {
        val source = if (isSpanishSource) TranslateLanguage.SPANISH else TranslateLanguage.ENGLISH
        val target = if (isSpanishSource) TranslateLanguage.ENGLISH else TranslateLanguage.SPANISH
        val locale = if (isSpanishSource) Locale.US else Locale("es", "ES")

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        
        val translator = Translation.getClient(options)
        translator.translate(text).addOnSuccessListener { result ->
            tvTranscript.append("\nIn: $text\nOut: $result\n")
            tts.language = locale
            tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, "ID")
        }
    }

    private fun setupTTS() { tts = TextToSpeech(this) {} }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }
}