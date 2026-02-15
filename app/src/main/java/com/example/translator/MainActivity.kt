package com.example.polyglotvoice

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
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

        btnListen.setOnClickListener { toggleListening() }
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { saveTranscript() }
        findViewById<ImageButton>(R.id.btnWhatsApp).setOnClickListener { shareToWhatsApp() }
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener { shareGeneral() }
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener { tvTranscript.text = "" }
        findViewById<ImageButton>(R.id.btnReset).setOnClickListener { setupTranslators() }
        
        findViewById<ToggleButton>(R.id.toggleFlavor).setOnCheckedChangeListener { _, isChecked -> 
            isRegionalFlavorEnabled = isChecked 
        }

        checkPermissions()
        initSpeechRecognizer()
        setupTTS()
        setupTranslators()
    }

    private fun initSpeechRecognizer() {
        if (speechRecognizer != null) speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    if (text.isNotEmpty()) detectAndTranslate(text)
                    else if (isListening) startListening()
                }
                override fun onError(p0: Int) { if (isListening) startListening() }
                override fun onReadyForSpeech(p0: Bundle?) { tvStatus.text = "LISTENING..." }
                override fun onBeginningOfSpeech() { triggerPulse(Color.WHITE) }
                override fun onRmsChanged(p0: Float) {}
                override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p0: Bundle?) {}
                override fun onEvent(p0: Int, p1: Bundle?) {}
            })
        }
    }

    private fun toggleListening() {
        if (isListening) {
            isListening = false
            speechRecognizer?.stopListening()
            btnListen.setColorFilter(Color.WHITE)
            tvStatus.text = "AI READY"
        } else {
            isListening = true
            btnListen.setColorFilter(Color.RED)
            startListening()
        }
    }

    private fun startListening() {
        if (isAiSpeaking) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        runOnUiThread { speechRecognizer?.startListening(intent) }
    }

    private fun detectAndTranslate(text: String) {
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { lang ->
            if (lang == "en") {
                triggerPulse(Color.parseColor("#006064"))
                performTranslation(text, enEsTranslator, Locale("es", "MX"))
            } else {
                triggerPulse(Color.parseColor("#FBC02D"))
                performTranslation(text, esEnTranslator, Locale.US)
            }
        }
    }

    private fun performTranslation(text: String, trans: Translator, loc: Locale) {
        isAiSpeaking = true
        trans.translate(text).addOnSuccessListener { result ->
            var output = result
            if (loc.language == "es" && isRegionalFlavorEnabled) {
                output = output.replace("niño", "chigüilín", true)
                               .replace("amigo", "compa", true)
                               .replace("trabajo", "chamba", true)
            }
            updateUI(text, output, loc.language == "es")
            tts.language = loc
            tts.speak(output, TextToSpeech.QUEUE_FLUSH, null, "ID")
        }.addOnFailureListener { isAiSpeaking = false }
    }

    private fun updateUI(input: String, output: String, toEs: Boolean) {
        val inCol = if (toEs) Color.parseColor("#006064") else Color.parseColor("#FBC02D")
        val outCol = if (toEs) Color.parseColor("#FBC02D") else Color.parseColor("#006064")
        val builder = SpannableStringBuilder()
        builder.append(SpannableString(" IN: $input \n").apply { 
            setSpan(BackgroundColorSpan(inCol), 0, length, 0)
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, 0)
        })
        builder.append(SpannableString(" OUT: $output \n\n").apply { 
            setSpan(BackgroundColorSpan(outCol), 0, length, 0)
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, 0)
        })
        runOnUiThread { 
            tvTranscript.append(builder)
            scrollTranscript.post { scrollTranscript.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun shareToWhatsApp() {
        val text = tvTranscript.text.toString()
        if (text.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try { startActivity(intent) } catch (e: Exception) { Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show() }
    }

    private fun shareGeneral() {
        val text = tvTranscript.text.toString()
        if (text.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share Logs"))
    }

    private fun saveTranscript() {
        val content = tvTranscript.text.toString()
        if (content.isEmpty()) return
        val name = "log_${System.currentTimeMillis()}.txt"
        openFileOutput(name, Context.MODE_PRIVATE).use { it.write(content.toByteArray()) }
        Toast.makeText(this, "Logged as $name", Toast.LENGTH_SHORT).show()
    }

    private fun triggerPulse(color: Int) {
        runOnUiThread {
            pulseIndicator.backgroundTintList = ColorStateList.valueOf(color)
            val sX = ObjectAnimator.ofFloat(pulseIndicator, "scaleX", 1f, 4f)
            val sY = ObjectAnimator.ofFloat(pulseIndicator, "scaleY", 1f, 4f)
            val al = ObjectAnimator.ofFloat(pulseIndicator, "alpha", 0.6f, 0f)
            AnimatorSet().apply { playTogether(sX, sY, al); duration = 500; start() }
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

    private fun setupTranslators() {
        val conditions = DownloadConditions.Builder().build()
        enEsTranslator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build())
        esEnTranslator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build())
        tvStatus.text = "PREPARING AI..."
        enEsTranslator.downloadModelIfNeeded(conditions).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(conditions).addOnSuccessListener {
                runOnUiThread { tvStatus.text = "AI READY" }
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != 0) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}