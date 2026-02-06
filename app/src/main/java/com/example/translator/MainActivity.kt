package com.example.polyglotvoice

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.*
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var tvTranscript: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnListen: ImageButton
    private lateinit var llDownload: LinearLayout
    private lateinit var pbModel: ProgressBar
    private lateinit var pulse1: View
    private lateinit var pulse2: View

    // Logic Engines
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var enEsTranslator: Translator
    private lateinit var esEnTranslator: Translator
    private val modelManager = RemoteModelManager.getInstance()

    // State Flags
    private var isListening = false
    private var isAiSpeaking = false
    private var conversationHistory = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI
        tvTranscript = findViewById(R.id.tvTranscript)
        tvStatus = findViewById(R.id.tvStatus)
        btnListen = findViewById(R.id.btnListen)
        llDownload = findViewById(R.id.llDownload)
        pbModel = findViewById(R.id.pbModel)
        pulse1 = findViewById(R.id.pulse1)
        pulse2 = findViewById(R.id.pulse2)

        checkPermissions()
        setupTTS()
        setupTranslators()

        btnListen.setOnClickListener {
            if (isListening) stopContinuousMode() else startContinuousMode()
        }
    }

    private fun setupTranslators() {
        llDownload.visibility = View.VISIBLE
        val optionsEnEs = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        val optionsEsEn = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build()

        enEsTranslator = Translation.getClient(optionsEnEs)
        esEnTranslator = Translation.getClient(optionsEsEn)

        val conditions = DownloadConditions.Builder().requireWifi().build()
        enEsTranslator.downloadModelIfNeeded(conditions).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(conditions).addOnSuccessListener {
                llDownload.visibility = View.GONE
                tvStatus.text = "Status: AI Ready"
            }
        }.addOnFailureListener { tvStatus.text = "Status: Download Failed" }
    }

    private fun startContinuousMode() {
        if (isAiSpeaking) return
        isListening = true
        startPulseAnimation(Color.LTGRAY)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                tvTranscript.text = "${conversationHistory}\nPresent: $text"
            }
            override fun onResults(results: Bundle?) {
                val finalResult = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                finalResult?.let { detectAndTranslate(it) }
            }
            override fun onError(error: Int) { if (isListening && !isAiSpeaking) startContinuousMode() }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun detectAndTranslate(text: String) {
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { langCode ->
            when (langCode) {
                "es" -> {
                    updatePulseColor(Color.RED)
                    translateAndSpeak(text, esEnTranslator, Locale.US)
                }
                "en" -> {
                    updatePulseColor(Color.BLUE)
                    translateAndSpeak(text, enEsTranslator, Locale("es", "ES"))
                }
                else -> if (isListening) startContinuousMode()
            }
        }
    }

    private fun translateAndSpeak(text: String, translator: Translator, locale: Locale) {
        isAiSpeaking = true
        speechRecognizer?.stopListening()
        
        translator.translate(text).addOnSuccessListener { translated ->
            conversationHistory.append("\nIn: $text\nOut: $translated\n---\n")
            tvTranscript.text = conversationHistory.toString()
            tts.language = locale
            tts.speak(translated, TextToSpeech.QUEUE_FLUSH, null, "UTT")
        }
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { 
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(id: String?) {
                    isAiSpeaking = false
                    runOnUiThread { if (isListening) startContinuousMode() }
                }
                override fun onStart(id: String?) { isAiSpeaking = true }
                override fun onError(id: String?) { isAiSpeaking = false }
            })
        }
    }

    private fun startPulseAnimation(color: Int) {
        val pulses = listOf(pulse1, pulse2)
        pulses.forEachIndexed { index, view ->
            view.visibility = View.VISIBLE
            view.backgroundTintList = ColorStateList.valueOf(color)
            val sX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 3f)
            val sY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 3f)
            val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
            AnimatorSet().apply {
                duration = 1500
                startDelay = (index * 750).toLong()
                playTogether(sX, sY, alpha)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { if (isListening) start() }
                })
                start()
            }
        }
    }

    private fun updatePulseColor(color: Int) {
        pulse1.backgroundTintList = ColorStateList.valueOf(color)
        pulse2.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun stopContinuousMode() {
        isListening = false
        pulse1.visibility = View.INVISIBLE
        pulse2.visibility = View.INVISIBLE
        speechRecognizer?.destroy()
    }

    // --- OPTIONS MENU ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_share -> shareTranscript()
            R.id.action_save -> saveConversation()
            R.id.action_clear -> { conversationHistory.clear(); tvTranscript.text = "" }
            R.id.action_reset_models -> deleteModels()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun shareTranscript() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, conversationHistory.toString())
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun saveConversation() {
        val name = "Chat_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.txt"
        openFileOutput(name, Context.MODE_PRIVATE).use { it.write(conversationHistory.toString().toByteArray()) }
        Toast.makeText(this, "Saved $name", Toast.LENGTH_SHORT).show()
    }

    private fun deleteModels() {
        val en = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
        val es = TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()
        modelManager.deleteDownloadedModel(en)
        modelManager.deleteDownloadedModel(es).addOnSuccessListener { setupTranslators() }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }
}