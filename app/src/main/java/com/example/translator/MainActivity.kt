package com.example.polyglotvoice

import android.Manifest
import android.animation.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.*
import android.speech.tts.*
import android.text.*
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.*
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tvTranscript: TextView
    private lateinit var scrollTranscript: ScrollView
    private lateinit var pulse1: View
    private lateinit var pulse2: View
    private lateinit var tvStatus: TextView
    private var isRegionalFlavorEnabled = false
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var enEsTranslator: Translator
    private lateinit var esEnTranslator: Translator
    private var isListening = false
    private var isAiSpeaking = false

    private val BG_CYAN = Color.parseColor("#006064")
    private val BG_YELLOW = Color.parseColor("#FBC02D")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvTranscript = findViewById(R.id.tvTranscript)
        scrollTranscript = findViewById(R.id.scrollTranscript)
        pulse1 = findViewById(R.id.pulse1)
        pulse2 = findViewById(R.id.pulse2)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { saveAndShare() }
        findViewById<ImageButton>(R.id.btnReset).setOnClickListener { resetModels() }
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener { tvTranscript.setText("", TextView.BufferType.SPANNABLE) }
        findViewById<ToggleButton>(R.id.toggleRegional).setOnCheckedChangeListener { _, isChecked -> isRegionalFlavorEnabled = isChecked }
        findViewById<ImageButton>(R.id.btnListen).setOnClickListener { if (isListening) stopContinuousMode() else startContinuousMode() }

        checkPermissions()
        setupTTS()
        setupTranslators()
    }

    private fun setupTranslators() {
        val enEs = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        val esEn = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build()
        enEsTranslator = Translation.getClient(enEs)
        esEnTranslator = Translation.getClient(esEn)
        
        val cond = DownloadConditions.Builder().requireWifi().build()
        tvStatus.text = "Downloading AI Models..."
        
        enEsTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
                runOnUiThread { tvStatus.text = "AI READY" }
            }
        }
    }

    private fun startContinuousMode() {
        if (isAiSpeaking) return
        isListening = true
        startPulse(Color.GRAY)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { runOnUiThread { tvStatus.text = "Listening..." } }
            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > 5.0f && isListening) { runOnUiThread { pulse1.visibility = View.VISIBLE; pulse1.alpha = 0.4f } }
            }
            override fun onError(e: Int) { if (isListening && !isAiSpeaking) startContinuousMode() }
            override fun onResults(r: Bundle?) { r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { detectLanguage(it) } }
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(i: Int, b: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun detectLanguage(text: String) {
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { lang ->
            if (lang == "en") {
                runOnUiThread { startPulse(BG_CYAN) }
                translateAndSpeak(text, enEsTranslator, Locale("es", "MX"))
            } else {
                runOnUiThread { startPulse(BG_YELLOW) }
                translateAndSpeak(text, esEnTranslator, Locale.US)
            }
        }
    }

    private fun translateAndSpeak(text: String, trans: Translator, loc: Locale) {
        isAiSpeaking = true
        trans.translate(text).addOnSuccessListener { res ->
            val out = if (loc.language == "es" && isRegionalFlavorEnabled) applyColimaFlavor(res) else res
            val inBg = if (loc.language == "es") BG_CYAN else BG_YELLOW
            val outBg = if (loc.language == "es") BG_YELLOW else BG_CYAN

            val builder = SpannableStringBuilder()
            val sIn = SpannableString(" In: $text \n").apply { 
                setSpan(BackgroundColorSpan(inBg), 0, length - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val sOut = SpannableString(" Out: $out \n").apply { 
                setSpan(BackgroundColorSpan(outBg), 0, length - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            builder.append(sIn).append("\n").append(sOut).append("\n")
            runOnUiThread {
                tvTranscript.append(builder)
                scrollTranscript.post { scrollTranscript.fullScroll(View.FOCUS_DOWN) }
            }
            tts.language = loc
            tts.speak(out, TextToSpeech.QUEUE_FLUSH, null, "UTT")
        }
    }

    private fun applyColimaFlavor(t: String): String {
        var r = t
        val dict = mapOf("niño" to "chigüilín", "amigo" to "compa", "trabajo" to "chamba", "dinero" to "feria", "autobús" to "la ruta")
        for ((k, v) in dict) r = r.replace("(?i)\\b$k\\b".toRegex(), v)
        return r
    }

    private fun startPulse(color: Int) {
        runOnUiThread {
            listOf(pulse1, pulse2).forEach { v ->
                v.visibility = View.VISIBLE
                v.backgroundTintList = ColorStateList.valueOf(color)
                val sX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 4f)
                val sY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 4f)
                val alpha = ObjectAnimator.ofFloat(v, "alpha", 0.5f, 0f)
                AnimatorSet().apply {
                    duration = 1500
                    playTogether(sX, sY, alpha)
                    addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { if (isListening) start() } })
                    start()
                }
            }
        }
    }

    private fun stopContinuousMode() { 
        isListening = false
        runOnUiThread { tvStatus.text = "AI READY"; pulse1.visibility = View.INVISIBLE; pulse2.visibility = View.INVISIBLE }
        speechRecognizer?.destroy() 
    }

    private fun checkPermissions() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1) }

    private fun setupTTS() { 
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() { 
                    override fun onDone(id: String?) { isAiSpeaking = false; if (isListening) runOnUiThread { startContinuousMode() } }
                    override fun onStart(id: String?) { isAiSpeaking = true }
                    override fun onError(id: String?) { isAiSpeaking = false } 
                })
            }
        } 
    }

    private fun saveAndShare() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val content = tvTranscript.text.toString()
        try {
            val file = File(getExternalFilesDir(null), "Polyglot_$timestamp.txt")
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, content) }
            startActivity(Intent.createChooser(intent, "Share via..."))
        } catch (e: Exception) { Toast.makeText(this, "Save Error", Toast.LENGTH_SHORT).show() }
    }

    private fun resetModels() { 
        val manager = RemoteModelManager.getInstance()
        manager.deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build())
        manager.deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()).addOnSuccessListener { setupTranslators() } 
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}