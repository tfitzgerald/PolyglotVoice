package com.example.polyglotvoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        scrollTranscript = findViewById(R.id.scrollTranscript)
        tvStatus = findViewById(R.id.tvStatus)
        pulseIndicator = findViewById(R.id.pulseIndicator)
        btnListen = findViewById(R.id.btnListen)
        toggleRegional = findViewById(R.id.toggleRegional)

        btnListen.setOnClickListener { if (isListening) stopListening() else startListening() }
        toggleRegional.setOnCheckedChangeListener { _, isChecked -> isRegionalFlavorEnabled = isChecked }

        checkPermissions()
        setupTTS()
        setupTranslators()
    }

    private fun setupTranslators() {
        val optionsEnEs = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        val optionsEsEn = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build()
        enEsTranslator = Translation.getClient(optionsEnEs)
        esEnTranslator = Translation.getClient(optionsEsEn)

        val cond = DownloadConditions.Builder().build()
        enEsTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
                runOnUiThread { tvStatus.text = "AI READY" }
            }
        }
    }

    private fun startListening() {
        if (isAiSpeaking) return
        isListening = true
        tvStatus.text = "Listening..."
        pulseIndicator.visibility = View.VISIBLE
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    detectAndTranslate(text)
                }
                override fun onError(p0: Int) { if (isListening) startListening() }
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
                performTranslation(text, enEsTranslator, Locale("es", "MX"))
            } else {
                performTranslation(text, esEnTranslator, Locale.US)
            }
        }
    }

    private fun performTranslation(text: String, trans: Translator, loc: Locale) {
        isAiSpeaking = true
        trans.translate(text).addOnSuccessListener { result ->
            val finalOutput = if (loc.language == "es" && isRegionalFlavorEnabled) {
                result.replace("niño", "chigüilín").replace("amigo", "compa")
            } else result
            
            updateUI(text, finalOutput, loc.language == "es")
            tts.language = loc
            tts.speak(finalOutput, TextToSpeech.QUEUE_FLUSH, null, "ID")
        }.addOnFailureListener { isAiSpeaking = false }
    }

    private fun updateUI(input: String, output: String, toEs: Boolean) {
        val inCol = if (toEs) Color.parseColor("#006064") else Color.parseColor("#FBC02D")
        val outCol = if (toEs) Color.parseColor("#FBC02D") else Color.parseColor("#006064")
        
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

    private fun setupTTS() {
        tts = TextToSpeech(this) { 
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(p0: String?) { isAiSpeaking = true }
                override fun onDone(p0: String?) { 
                    isAiSpeaking = false
                    if (isListening) runOnUiThread { startListening() } 
                }
                override fun onError(p0: String?) { isAiSpeaking = false }
            })
        }
    }

    private fun stopListening() { 
        isListening = false
        tvStatus.text = "AI READY"
        pulseIndicator.visibility = View.INVISIBLE
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