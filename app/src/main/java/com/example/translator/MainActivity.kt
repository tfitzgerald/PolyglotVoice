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
import android.text.style.BackgroundColorSpan
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

    // HIGH-CONTRAST COLORS (System-Proof)
    private val BG_CYAN = Color.parseColor("#008B8B")   // Darker Cyan for readability
    private val BG_YELLOW = Color.parseColor("#8B8000") // Darker Yellow for readability
    private val TEXT_WHITE = Color.WHITE

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
            val out = if (loc.language == "es" && isRegionalFlavorEnabled) applyColimaFlavor(res) else res
            
            // UI Color Assignment: English = Cyan, Spanish = Yellow
            val inBg = if (loc.language == "es") BG_YELLOW else BG_CYAN
            val outBg = if (loc.language == "es") BG_CYAN else BG_YELLOW

            val builder = SpannableStringBuilder()

            // Styled "In" line with background color bubble
            val sIn = SpannableString(" In: $text \n")
            sIn.setSpan(BackgroundColorSpan(inBg), 0, sIn.length - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sIn.setSpan(ForegroundColorSpan(TEXT_WHITE), 0, sIn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sIn.setSpan(StyleSpan(Typeface.BOLD), 1, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Styled "Out" line with background color bubble
            val sOut = SpannableString(" Out: $out \n")
            sOut.setSpan(BackgroundColorSpan(outBg), 0, sOut.length - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sOut.setSpan(ForegroundColorSpan(TEXT_WHITE), 0, sOut.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sOut.setSpan(StyleSpan(Typeface.BOLD), 1, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

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
        val dictionary = mapOf(
            "niño" to "chigüilín", 
            "amigo" to "compa", 
            "trabajo" to "chamba", 
            "dinero" to "feria",
            "autobús" to "la ruta"
        )
        for ((k, v) in dictionary) r = r.replace("(?i)\\b$k\\b".toRegex(), v)
        return r
    }

    private fun saveAndShare() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val content = tvTranscript.text.toString()
        try {
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "Polyglot_$timestamp.txt")
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(intent, "Share via WhatsApp/DM"))
        } catch (e: Exception) { Toast.makeText(this, "Save Failed", Toast.LENGTH_SHORT).show() }
    }

    private fun setupTranslators() {
        val enEs = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        val esEn = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build()
        enEsTranslator = Translation.getClient(enEs)
        esEnTranslator = Translation.getClient(esEn)
        val cond = DownloadConditions.Builder().requireWifi().build()
        enEsTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(cond).addOnSuccessListener { 
                findViewById<TextView>(R.id.tvStatus).text = "AI READY" 
            }
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
        startPulse(Color.DKGRAY) // Neutral pulse while waiting
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onRmsChanged(rmsdB: Float) {
                // Real-time voice feedback
                if (rmsdB > 2f) { pulse1.visibility = View.VISIBLE; pulse1.alpha = 0.4f }
            }
            override fun onResults(r: Bundle?) { 
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { 
                    detectLanguage(it) 
                } 
            }
            override fun onError(e: Int) { if (isListening && !isAiSpeaking) startContinuousMode() }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun detectLanguage(text: String) {
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { lang ->
            val pulseColor = if (lang == "es") BG_YELLOW else BG_CYAN
            runOnUiThread { startPulse(pulseColor) } // Identification Pulse

            if (lang == "es") translateAndSpeak(text, esEnTranslator, Locale.US)
            else translateAndSpeak(text, enEsTranslator, Locale("es", "MX"))
        }
    }

    private fun startPulse(color: Int) {
        runOnUiThread {
            listOf(pulse1, pulse2).forEach { v ->
                v.visibility = View.VISIBLE
                v.backgroundTintList = ColorStateList.valueOf(color)
                val sX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 4f)
                val sY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 4f)
                val alpha = ObjectAnimator.ofFloat(v, "alpha", 0.6f, 0f)
                AnimatorSet().apply {
                    duration = 1400
                    playTogether(sX, sY, alpha)
                    addListener(object : AnimatorListenerAdapter() { 
                        override fun onAnimationEnd(a: Animator) { if (isListening) start() } 
                    })
                    start()
                }
            }
        }
    }

    private fun stopContinuousMode() { isListening = false; pulse1.visibility = View.INVISIBLE; pulse2.visibility = View.INVISIBLE; speechRecognizer?.destroy() }
    private fun checkPermissions() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
    private fun resetModels() { val manager = RemoteModelManager.getInstance(); manager.deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()); manager.deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()).addOnSuccessListener { setupTranslators() } }
}