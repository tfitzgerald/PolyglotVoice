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
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var tvTranscript: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnListen: ImageButton
    
    private var isListening = false
    private var isAiSpeaking = false // FLAG TO PREVENT SELF-TALK
    private var pulseAnimator: ObjectAnimator? = null
    
    private lateinit var enEsTranslator: Translator
    private lateinit var esEnTranslator: Translator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        tvStatus = findViewById(R.id.tvStatus)
        btnListen = findViewById(R.id.btnListen)
        findViewById<Button>(R.id.btnClear).setOnClickListener { tvTranscript.text = "" }

        setupTTS()
        checkPermissions()
        setupTranslators()

        btnListen.setOnClickListener {
            if (isListening) stopContinuousSpeech() else {
                isListening = true
                startContinuousSpeech()
            }
        }
    }

    private fun setupTranslators() {
        tvStatus.text = "Status: Loading AI..."
        val enEsOptions = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        val esEnOptions = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build()

        enEsTranslator = Translation.getClient(enEsOptions)
        esEnTranslator = Translation.getClient(esEnOptions)

        val conditions = DownloadConditions.Builder().requireWifi().build()
        enEsTranslator.downloadModelIfNeeded(conditions).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(conditions).addOnSuccessListener {
                tvStatus.text = "Status: Ready (Auto-Detect)"
            }
        }
    }

    private fun startContinuousSpeech() {
        if (!isListening || isAiSpeaking) return // DON'T START IF AI IS TALKING

        // Pulse Animation
        if (pulseAnimator == null) {
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(btnListen,
                PropertyValuesHolder.ofFloat("scaleX", 1.25f),
                PropertyValuesHolder.ofFloat("scaleY", 1.25f)
            ).apply {
                duration = 600
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
        }
        if (pulseAnimator?.isStarted == false) pulseAnimator?.start()

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                text?.let { runLanguageId(it) }
                // Only loop if AI isn't about to speak
                if (isListening && !isAiSpeaking) startContinuousSpeech() 
            }
            override fun onError(error: Int) { 
                if (isListening && !isAiSpeaking) startContinuousSpeech() 
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

    private fun runLanguageId(text: String) {
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text).addOnSuccessListener { languageCode ->
            when (languageCode) {
                "es" -> {
                    btnListen.backgroundTintList = ColorStateList.valueOf(Color.RED)
                    performTranslation(text, esEnTranslator, Locale.US)
                }
                "en" -> {
                    btnListen.backgroundTintList = ColorStateList.valueOf(Color.BLUE)
                    performTranslation(text, enEsTranslator, Locale("es", "ES"))
                }
                else -> performTranslation(text, enEsTranslator, Locale("es", "ES"))
            }
        }
    }

    private fun performTranslation(text: String, activeTranslator: Translator, targetLocale: Locale) {
        activeTranslator.translate(text).addOnSuccessListener { translated ->
            tvTranscript.append("\nIn: $text\nOut: $translated\n")
            
            // PREVENT SELF-TALK: Set flag and stop recognizer
            isAiSpeaking = true
            speechRecognizer?.stopListening()
            
            tts.language = targetLocale
            // We pass a unique ID ("UTT_ID") to trigger the progress listener
            tts.speak(translated, TextToSpeech.QUEUE_FLUSH, null, "UTT_ID")
        }
    }

    private fun setupTTS() { 
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isAiSpeaking = true
                    }
                    override fun onDone(utteranceId: String?) {
                        isAiSpeaking = false
                        // RESTART LISTENING AFTER AI FINISHES
                        runOnUiThread { if (isListening) startContinuousSpeech() }
                    }
                    override fun onError(utteranceId: String?) {
                        isAiSpeaking = false
                        runOnUiThread { if (isListening) startContinuousSpeech() }
                    }
                })
            }
        }
    }

    private fun stopContinuousSpeech() {
        isListening = false
        isAiSpeaking = false
        pulseAnimator?.end()
        btnListen.scaleX = 1f
        btnListen.scaleY = 1f
        btnListen.backgroundTintList = ColorStateList.valueOf(Color.DKGRAY)
        speechRecognizer?.destroy()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts.shutdown()
    }
}