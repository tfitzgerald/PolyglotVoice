package com.example.polyglotvoice

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
// Corrected this import line below:
import com.google.mlkit.nl.translate.* // Added this crucial line below:
import com.example.polyglotvoice.R 

import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var tvTranscript: TextView
    private lateinit var downloadSection: LinearLayout
    private val langIdentifier = LanguageIdentification.getClient()
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        downloadSection = findViewById(R.id.downloadSection)

        checkPermissions()
        setupTTS()
        setupSpeechRecognizer()
        prepareModels()

        findViewById<View>(R.id.btnListen).setOnClickListener {
            if (!isListening) startListening() else stopListening()
        }
    }

    private fun startListening() {
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer.stopListening()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                text?.let { processAudioResult(it) }
            }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(p0: Int) { isListening = false }
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
    }

    private fun processAudioResult(text: String) {
        langIdentifier.identifyLanguage(text).addOnSuccessListener { langCode ->
            when (langCode) {
                "es" -> translate(text, TranslateLanguage.SPANISH, TranslateLanguage.ENGLISH, Locale.US)
                "en" -> translate(text, TranslateLanguage.ENGLISH, TranslateLanguage.SPANISH, Locale("es", "ES"))
                else -> Toast.makeText(this, "Language not recognized", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun translate(text: String, source: String, target: String, locale: Locale) {
        val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        val translator = Translation.getClient(options)
        
        translator.translate(text).addOnSuccessListener { result ->
            tvTranscript.append("\nMe: $text\nAI: $result\n")
            tts.language = locale
            tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, "ID")
        }
    }

    private fun prepareModels() {
        val manager = RemoteModelManager.getInstance()
        val conditions = DownloadConditions.Builder().requireWifi().build()
        manager.download(TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build(), conditions)
        manager.download(TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build(), conditions)
            .addOnSuccessListener { downloadSection.visibility = View.GONE }
    }

    private fun setupTTS() { tts = TextToSpeech(this) {} }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_share) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, tvTranscript.text.toString())
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        }
        return true
    }
}