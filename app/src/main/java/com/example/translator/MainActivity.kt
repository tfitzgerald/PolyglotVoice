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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.nl.translate.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var tvTranscript: TextView
    private lateinit var btnListen: View 
    private var isListening = false
    private var isSpanishSource = true 
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        btnListen = findViewById(R.id.btnListen)
        val btnClear = findViewById<Button>(R.id.btnClear)

        setupTTS()
        checkPermissions()

        btnListen.setOnClickListener {
            if (!isListening) {
                isListening = true
                startListeningLoop()
            } else {
                isListening = false
                stopListening()
            }
        }

        btnClear.setOnClickListener {
            tvTranscript.text = ""
        }
    }

    private fun startListeningLoop() {
        // Prepare the Pulse Animation
        val color = if (isSpanishSource) Color.RED else Color.BLUE
        btnListen.backgroundTintList = ColorStateList.valueOf(color)
        
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            btnListen,
            PropertyValuesHolder.ofFloat("scaleX", 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1.2f)
        ).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isSpanishSource) "es-ES" else "en-US")
        }

        speechRecognizer?.destroy() // Clean up old recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                text?.let { processTranslation(it) }
                if (isListening) startListeningLoop() // Loop back
            }

            override fun onError(error: Int) {
                if (isListening) startListeningLoop() // Restart on silence/timeout
            }

            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        pulseAnimator?.cancel()
        btnListen.scaleX = 1f
        btnListen.scaleY = 1f
        btnListen.backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
    }

    private fun processTranslation(text: String) {
        val source = if (isSpanishSource) TranslateLanguage.SPANISH else TranslateLanguage.ENGLISH
        val target = if (isSpanishSource) TranslateLanguage.ENGLISH else TranslateLanguage.SPANISH
        val locale = if (isSpanishSource) Locale.US else Locale("es", "ES")

        val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        val translator = Translation.getClient(options)

        translator.translate(text).addOnSuccessListener { result ->
            val timestamp = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            tvTranscript.append("\n[$timestamp]\nIn: $text\nOut: $result\n")
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

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts.stop()
        tts.shutdown()
    }
}