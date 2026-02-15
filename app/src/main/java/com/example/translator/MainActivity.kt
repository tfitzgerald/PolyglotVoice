package com.example.polyglotvoice

import android.Manifest
import android.animation.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.*
import android.speech.tts.*
import android.text.*
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
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
    private var isRegionalFlavorEnabled = false
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var enEsTranslator: Translator
    private lateinit var esEnTranslator: Translator
    private var isListening = false
    private var isAiSpeaking = false

    // Hardware-Level Colors
    private val CYAN = 0xFF00FFFF.toInt()
    private val YELLOW = 0xFFFFFF00.toInt()
    private val GRAY = 0xFF666666.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        scrollTranscript = findViewById(R.id.scrollTranscript)
        pulse1 = findViewById(R.id.pulse1)
        pulse2 = findViewById(R.id.pulse2)

        findViewById<Button>(R.id.btnClear).setOnClickListener { tvTranscript.setText("", TextView.BufferType.SPANNABLE) }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveAndShare() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetModels() }
        findViewById<ToggleButton>(R.id.toggleRegional).setOnCheckedChangeListener { _, isChecked -> isRegionalFlavorEnabled = isChecked }
        findViewById<ImageButton>(R.id.btnListen).setOnClickListener { if (isListening) stopContinuousMode() else startContinuousMode() }

        checkPermissions()
        setupTTS()
        setupTranslators()
    }

    private fun translateAndSpeak(text: String, trans: Translator, loc: Locale) {
        isAiSpeaking = true
        speechRecognizer?.stopListening()
        
        trans.translate(text).addOnSuccessListener { res ->
            val out = if (loc.language == "es" && isRegionalFlavorEnabled) applyManzanilloFlavor(res) else res
            
            val inColor = if (loc.language == "es") YELLOW else CYAN
            val outColor = if (loc.language == "es") CYAN else YELLOW

            runOnUiThread { startPulse(outColor) }

            val sIn = SpannableStringBuilder("In: $text\n")
            sIn.setSpan(ForegroundColorSpan(inColor), 0, sIn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sIn.setSpan(StyleSpan(Typeface.BOLD), 0, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            val sOut = SpannableStringBuilder("Out: $out\n")
            sOut.setSpan(ForegroundColorSpan(outColor), 0, sOut.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sOut.setSpan(StyleSpan(Typeface.BOLD), 0, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            val div = SpannableString("────────────────\n")
            div.setSpan(ForegroundColorSpan(GRAY), 0, div.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            runOnUiThread {
                tvTranscript.append(sIn)
                tvTranscript.append(sOut)
                tvTranscript.append(div)
                scrollTranscript.post { scrollTranscript.fullScroll(View.FOCUS_DOWN) }
            }

            tts.language = loc
            tts.speak(out, TextToSpeech.QUEUE_FLUSH, null, "UTT")
        }
    }

    private fun applyManzanilloFlavor(t: String): String {
        var r = t
        val d = mapOf("niño" to "chigüilín", "amigo" to "compa", "trabajo" to "chamba", "dinero" to "feria", "autobús" to "la ruta")
        for ((k, v) in d) r = r.replace("(?i)\\b$k\\b".toRegex(), v)
        return r
    }

    private fun saveAndShare() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val content = tvTranscript.text.toString()
        try {
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "Transcript_$timestamp.txt")
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(intent, "Share via WhatsApp/DM"))
        } catch (e: Exception) { Toast.makeText(this, "Save Failed", Toast.LENGTH_SHORT).show() }
    }

    private fun setupTranslators() {
        val options = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        enEsTranslator = Translation.getClient(options)
        esEnTranslator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build())
        val cond = DownloadConditions.Builder().requireWifi().build()
        enEsTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(cond).addOnSuccessListener { findViewById<TextView>(R.id.tvStatus).text = "AI Ready" }
        }
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(id: String?) { isAiSpeaking = false; if (isListening) runOnUiThread { startContinuousMode() } }
                override fun onStart(id: String?) { isAiSpeaking = true }
                override fun onError(id: String?) { isAiSpeaking = false }
            })
        }
    }

    private fun startContinuousMode() {
        if (isAiSpeaking) return
        isListening = true
        startPulse(Color.LTGRAY)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) { r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { detect(it) } }
            override fun onError(e: Int) { if (isListening && !isAiSpeaking) startContinuousMode() }
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

    private fun detect(text: String) {
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { lang ->
            if (lang == "es") translateAndSpeak(text, esEnTranslator, Locale.US)
            else translateAndSpeak(text, enEsTranslator, Locale("es", "MX"))
        }
    }

    private fun startPulse(color: Int) {
        listOf(pulse1, pulse2).forEach { v ->
            v.visibility = View.VISIBLE
            v.backgroundTintList = ColorStateList.valueOf(color)
            val sX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 3f)
            val sY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 3f)
            val alpha = ObjectAnimator.ofFloat(v, "alpha", 0.6f, 0f)
            AnimatorSet().apply {
                duration = 1500
                playTogether(sX, sY, alpha)
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { if (isListening) start() } })
                start()
            }
        }
    }

    private fun stopContinuousMode() { isListening = false; pulse1.visibility = View.INVISIBLE; pulse2.visibility = View.INVISIBLE; speechRecognizer?.destroy() }
    private fun checkPermissions() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
    private fun resetModels() { val manager = RemoteModelManager.getInstance(); manager.deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()); manager.deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()).addOnSuccessListener { setupTranslators() } }
}