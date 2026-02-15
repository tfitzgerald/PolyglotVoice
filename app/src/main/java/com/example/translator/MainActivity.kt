package com.example.polyglotvoice

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
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
    private lateinit var tvTranscript: TextView
    private lateinit var scrollTranscript: ScrollView
    private lateinit var tvStatus: TextView
    private lateinit var pulseIndicator: View
    private lateinit var btnListen: ImageButton
    private lateinit var toggleRegional: ToggleButton
    
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var enEsTranslator: Translator
    private lateinit var esEnTranslator: Translator
    
    private var isListening = false
    private var isAiSpeaking = false
    private var isRegionalFlavorEnabled = false

    // Color constants for the bubbles
    private val COLOR_EN = Color.parseColor("#006064") // Dark Cyan
    private val COLOR_ES = Color.parseColor("#FBC02D") // Amber/Yellow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        scrollTranscript = findViewById(R.id.scrollTranscript)
        tvStatus = findViewById(R.id.tvStatus)
        pulseIndicator = findViewById(R.id.pulseIndicator)
        btnListen = findViewById(R.id.btnListen)
        toggleRegional = findViewById(R.id.toggleRegional)

        btnListen.setOnClickListener { 
            if (isListening) stopListening() else startListening() 
        }
        
        toggleRegional.setOnCheckedChangeListener { _, isChecked -> 
            isRegionalFlavorEnabled = isChecked 
        }

        checkPermissions()
        setupTTS()
        setupTranslators()
    }

    private fun setupTranslators() {
        val optionsEnEs = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH).build()
        val optionsEsEn = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH).build()

        enEsTranslator = Translation.getClient(optionsEnEs)
        esEnTranslator = Translation.getClient(optionsEsEn)

        // No requireWifi() to ensure functionality in Tecomán on cellular
        val cond = DownloadConditions.Builder().build()
        tvStatus.text = "Downloading AI Models..."

        enEsTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
                runOnUiThread { tvStatus.text = "AI READY" }
            }
        }.addOnFailureListener { e ->
            runOnUiThread { tvStatus.text = "Error: ${e.message}" }
        }
    }

    private fun startListening() {
        if (isAiSpeaking) return
        isListening = true
        runOnUiThread { 
            tvStatus.text = "Listening..."
            triggerPulse(Color.GRAY) 
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    detectAndTranslate(text)
                }
                override fun onError(p0: Int) { 
                    if (isListening && !isAiSpeaking) startListening() 
                }
                override fun onReadyForSpeech(p0: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(p0: Float) {}
                override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p0: Bundle?) {}
                override fun onEvent(p0: Int, p1: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun detectAndTranslate(text: String) {
        if (text.isBlank()) return
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { lang ->
            if (lang == "en") {
                triggerPulse(COLOR_EN)
                performTranslation(text, enEsTranslator, Locale("es", "MX"))
            } else {
                triggerPulse(COLOR_ES)
                performTranslation(text, esEnTranslator, Locale.US)
            }
        }
    }

    private fun performTranslation(text: String, trans: Translator, loc: Locale) {
        isAiSpeaking = true
        // Clean text to avoid "The Oldest" errors
        val cleanInput = text.trim().replace(Regex("[.\\-_]"), "")
        
        trans.translate(cleanInput).addOnSuccessListener { result ->
            val finalOutput = if (loc.language == "es" && isRegionalFlavorEnabled) {
                result.replace("niño", "chigüilín")
                      .replace("amigo", "compa")
                      .replace("trabajo", "chamba")
            } else result
            
            updateTranscriptUI(cleanInput, finalOutput, loc.language == "es")
            
            tts.language = loc
            tts.speak(finalOutput, TextToSpeech.QUEUE_FLUSH, null, "ID")
        }.addOnFailureListener { 
            isAiSpeaking = false 
        }
    }

    private fun updateTranscriptUI(input: String, output: String, toEs: Boolean) {
        val inCol = if (toEs) COLOR_EN else COLOR_ES
        val outCol = if (toEs) COLOR_ES else COLOR_EN
        
        val builder = SpannableStringBuilder()
        val sIn = SpannableString(" IN: $input \n").apply { 
            setSpan(BackgroundColorSpan(inCol), 0, length, 0)
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, 0)
        }
        val sOut = SpannableString(" OUT: $output \n\n").apply { 
            setSpan(BackgroundColorSpan(outCol), 0, length, 0)
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, 0)
        }
        builder.append(sIn).append(sOut)
        
        runOnUiThread {
            tvTranscript.append(builder)
            scrollTranscript.post { scrollTranscript.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun triggerPulse(color: Int) {
        pulseIndicator.backgroundTintList = ColorStateList.valueOf(color)
        val scaleX = ObjectAnimator.ofFloat(pulseIndicator, "scaleX", 1f, 3f)
        val scaleY = ObjectAnimator.ofFloat(pulseIndicator, "scaleY", 1f, 3f)
        val alpha = ObjectAnimator.ofFloat(pulseIndicator, "alpha", 1f, 0f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 800
            start()
        }
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { 
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(p0: String?) { isAiSpeaking = true }
                override fun onDone(p0: String?) { 
                    isAiSpeaking = false
                    // Resume listening automatically for continuous flow
                    if (isListening) runOnUiThread { startListening() } 
                }
                override fun onError(p0: String?) { isAiSpeaking = false }
            })
        }
    }

    private fun stopListening() { 
        isListening = false
        tvStatus.text = "AI READY"
        speechRecognizer?.destroy() 
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != 0) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}