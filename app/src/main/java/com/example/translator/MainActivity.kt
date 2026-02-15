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
import android.text.Spannable
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
    
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var enEsTranslator: Translator
    private lateinit var esEnTranslator: Translator
    
    private var isListening = false
    private var isAiSpeaking = false
    private var isRegionalFlavorEnabled = false

    private val BG_EN = Color.parseColor("#006064")
    private val BG_ES = Color.parseColor("#FBC02D")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Triple-checked: Layout and IDs are generated first
        setupUnifiedLayout()

        checkPermissions()
        setupTTS()
        setupTranslators()
    }

    private fun setupUnifiedLayout() {
        val root = RelativeLayout(this).apply { 
            layoutParams = RelativeLayout.LayoutParams(-1, -1)
            backgroundColor = Color.parseColor("#121212")
        }

        tvStatus = TextView(this).apply {
            id = View.generateViewId()
            text = "Initializing AI..."
            setTextColor(Color.GREEN)
            val params = RelativeLayout.LayoutParams(-2, -2)
            params.addRule(RelativeLayout.CENTER_HORIZONTAL)
            params.topMargin = 50
            layoutParams = params
        }

        scrollTranscript = ScrollView(this).apply {
            id = View.generateViewId()
            val params = RelativeLayout.LayoutParams(-1, -1)
            params.addRule(RelativeLayout.BELOW, tvStatus.id)
            params.addRule(RelativeLayout.ABOVE, 1001) 
            layoutParams = params
            setPadding(30, 30, 30, 30)
        }

        tvTranscript = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
        }
        scrollTranscript.addView(tvTranscript)

        val controls = LinearLayout(this).apply {
            id = 1001
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val params = RelativeLayout.LayoutParams(-1, -2)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            layoutParams = params
            setPadding(0, 0, 0, 50)
        }

        pulseIndicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(100, 100)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.presence_online)
            visibility = View.INVISIBLE
        }

        val btnListen = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            layoutParams = LinearLayout.LayoutParams(200, 200)
            setOnClickListener { if (isListening) stopListening() else startListening() }
        }

        val toggle = ToggleButton(this).apply {
            textOn = "Colima Slang: ON"; textOff = "Colima Slang: OFF"
            setOnCheckedChangeListener { _, isChecked -> isRegionalFlavorEnabled = isChecked }
        }

        controls.addView(pulseIndicator)
        controls.addView(toggle)
        controls.addView(btnListen)
        
        root.addView(tvStatus)
        root.addView(scrollTranscript)
        root.addView(controls)
        setContentView(root)
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
        runOnUiThread { 
            tvStatus.text = "Listening..."
            pulseIndicator.visibility = View.VISIBLE 
        }
        
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
            val finalOutput = if (loc.language == "es" && isRegionalFlavorEnabled) applySlang(result) else result
            updateUI(text, finalOutput, loc.language == "es")
            tts.language = loc
            tts.speak(finalOutput, TextToSpeech.QUEUE_FLUSH, null, "ID")
        }.addOnFailureListener { isAiSpeaking = false }
    }

    private fun updateUI(input: String, output: String, toEs: Boolean) {
        val inCol = if (toEs) BG_EN else BG_ES
        val outCol = if (toEs) BG_ES else BG_EN
        val builder = SpannableStringBuilder()
        builder.append(SpannableString(" IN: $input \n").apply { setSpan(BackgroundColorSpan(inCol), 0, length, 33) })
        builder.append(SpannableString(" OUT: $finalOutput \n\n").apply { 
            // Fixed reference to local finalOutput if needed, or simply use 'output'
            setSpan(BackgroundColorSpan(outCol), 0, length, 33) 
        })
        
        // Correction: Using 'output' directly to ensure no unresolved references
        val finalBuilder = SpannableStringBuilder()
        val sIn = SpannableString(" IN: $input \n").apply { setSpan(BackgroundColorSpan(inCol), 0, length, 33) }
        val sOut = SpannableString(" OUT: $output \n\n").apply { setSpan(BackgroundColorSpan(outCol), 0, length, 33) }
        finalBuilder.append(sIn).append(sOut)
        
        runOnUiThread {
            tvTranscript.append(finalBuilder)
            scrollTranscript.post { scrollTranscript.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun applySlang(t: String) = t.replace("niño", "chigüilín").replace("amigo", "compa")

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
        runOnUiThread { 
            tvStatus.text = "AI READY"
            pulseIndicator.visibility = View.INVISIBLE 
        }
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