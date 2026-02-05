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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var tvTranscript: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnListen: ImageButton
    
    private var isListening = false
    private var isSpanishSource = true 
    private var pulseAnimator: ObjectAnimator? = null
    private var translator: Translator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        tvStatus = findViewById(R.id.tvStatus)
        btnListen = findViewById(R.id.btnListen)
        val btnClear = findViewById<Button>(R.id.btnClear)

        checkPermissions()
        setupTTS()
        prepareTranslator()

        btnListen.setOnClickListener {
            if (isListening) stopContinuousSpeech() else startContinuousSpeech()
        }

        btnClear.setOnClickListener { tvTranscript.text = "" }
    }

    private fun prepareTranslator() {
        tvStatus.text = "Status: Downloading AI Models..."
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        translator = Translation.getClient(options)

        val conditions = DownloadConditions.Builder().requireWifi().build()
        translator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener { tvStatus.text = "Status: AI Ready" }
            ?.addOnFailureListener { tvStatus.text = "Status: Error downloading models" }
    }

    private fun startContinuousSpeech() {
        isListening = true
        tvStatus.text = "Status: Listening..."
        
        // Pulse Logic
        val color = if (isSpanishSource) Color.RED else Color.BLUE
        btnListen.imageTintList = ColorStateList.valueOf(color)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(btnListen,
            PropertyValuesHolder.ofFloat("scaleX", 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1.2f)).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isSpanishSource) "es-ES" else "en-US")
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                text?.let { translateAndSpeak(it) }
                if (isListening) startContinuousSpeech()
            }
            override fun onError(error: Int) { if (isListening) startContinuousSpeech() }
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

    private fun translateAndSpeak(text: String) {
        translator?.translate(text)?.addOnSuccessListener { translatedText ->
            tvTranscript.append("\nMe: $text\nAI: $translatedText\n")
            tts.speak(translatedText, TextToSpeech.QUEUE_FLUSH, null, "ID")
        }
    }

    private fun stopContinuousSpeech() {
        isListening = false
        tvStatus.text = "Status: Stopped"
        pulseAnimator?.cancel()
        btnListen.scaleX = 1f
        btnListen.scaleY = 1f
        btnListen.imageTintList = null
        speechRecognizer?.destroy()
    }

    private fun setupTTS() { tts = TextToSpeech(this) {} }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
        speechRecognizer?.destroy()
        tts.shutdown()
    }
}