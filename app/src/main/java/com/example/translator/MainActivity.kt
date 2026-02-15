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
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.polyglotvoice.R
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        scrollTranscript = findViewById(R.id.scrollTranscript)
        pulse1 = findViewById(R.id.pulse1)
        pulse2 = findViewById(R.id.pulse2)

        // LISTENERS
        findViewById<Button>(R.id.btnClear).setOnClickListener { 
            tvTranscript.setText("", TextView.BufferType.SPANNABLE) 
        }
        
        findViewById<Button>(R.id.btnSave).setOnClickListener { 
            saveConversation() 
        }
        
        findViewById<Button>(R.id.btnReset).setOnClickListener { 
            resetModels() 
        }
        
        findViewById<ToggleButton>(R.id.toggleRegional).setOnCheckedChangeListener { _, isChecked ->
            isRegionalFlavorEnabled = isChecked
        }

        checkPermissions()
        setupTTS()
        setupTranslators()

        findViewById<ImageButton>(R.id.btnListen).setOnClickListener {
            if (isListening) stopContinuousMode() else startContinuousMode()
        }
    }

	private fun translateAndSpeak(text: String, trans: Translator, loc: Locale) {
		isAiSpeaking = true
		speechRecognizer?.stopListening()
		
		trans.translate(text).addOnSuccessListener { res ->
			val out = if (loc.language == "es" && isRegionalFlavorEnabled) applyFlavor(res) else res
			
			// Use high-saturation Hex colors for better visibility against dark backgrounds
			val inColor = if (loc.language == "es") Color.parseColor("#FFFF00") else Color.parseColor("#00FFFF")
			val outColor = if (loc.language == "es") Color.parseColor("#00FFFF") else Color.parseColor("#FFFF00")

			// Build the "In" line with color
			val sIn = SpannableString("In: $text\n")
			sIn.setSpan(ForegroundColorSpan(inColor), 0, sIn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			
			// Build the "Out" line with color
			val sOut = SpannableString("Out: $out\n")
			sOut.setSpan(ForegroundColorSpan(outColor), 0, sOut.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

			val divider = SpannableString("────────────────\n")
			divider.setSpan(ForegroundColorSpan(Color.DKGRAY), 0, divider.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

			runOnUiThread {
				// We append them one by one to ensure the buffer keeps the spans
				tvTranscript.append(sIn)
				tvTranscript.append(sOut)
				tvTranscript.append(divider)
				
				scrollTranscript.post { scrollTranscript.fullScroll(View.FOCUS_DOWN) }
			}

			tts.language = loc
			tts.speak(out, TextToSpeech.QUEUE_FLUSH, null, "UTT")
		}
	}

    private fun applyFlavor(t: String): String {
        var r = t
        val d = mapOf("niño" to "chigüilín", "amigo" to "compa", "trabajo" to "chamba", "dinero" to "feria")
        for ((k, v) in d) r = r.replace("(?i)\\b$k\\b".toRegex(), v)
        return r
    }

    private fun saveConversation() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "Log_$timestamp.txt"
        val content = tvTranscript.text.toString()

        try {
            // Save to app-specific internal docs to ensure success without permission drama
            val docsDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            val file = File(docsDir, fileName)
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            
            // Pop up Share Sheet so user can move the file anywhere
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(shareIntent, "Export Transcript"))
            Toast.makeText(this, "Saved to App Folder", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTranslators() {
        val enEs = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        val esEn = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build()
        enEsTranslator = Translation.getClient(enEs)
        esEnTranslator = Translation.getClient(esEn)
        
        val cond = DownloadConditions.Builder().requireWifi().build()
        enEsTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(cond).addOnSuccessListener { 
                findViewById<TextView>(R.id.tvStatus).text = "AI Ready" 
            }
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

    private fun startContinuousMode() {
        if (isAiSpeaking) return
        isListening = true
        startPulse(Color.LTGRAY)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) { 
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { detect(it) } 
            }
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
            when (lang) {
                "es" -> translateAndSpeak(text, esEnTranslator, Locale.US)
                "en" -> translateAndSpeak(text, enEsTranslator, Locale("es", "MX"))
                else -> if (isListening) startContinuousMode()
            }
        }
    }

    private fun startPulse(c: Int) {
        listOf(pulse1, pulse2).forEach { v ->
            v.visibility = View.VISIBLE
            v.backgroundTintList = ColorStateList.valueOf(c)
            val sX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 3f)
            val sY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 3f)
            val a = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f)
            AnimatorSet().apply {
                duration = 1500
                playTogether(sX, sY, a)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(anim: Animator) { if (isListening) start() }
                })
                start()
            }
        }
    }

    private fun resetModels() {
        val manager = RemoteModelManager.getInstance()
        val en = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
        val es = TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()
        manager.deleteDownloadedModel(en); manager.deleteDownloadedModel(es).addOnSuccessListener { setupTranslators() }
    }

    private fun stopContinuousMode() { 
        isListening = false
        pulse1.visibility = View.INVISIBLE
        pulse2.visibility = View.INVISIBLE
        speechRecognizer?.destroy() 
    }

    private fun checkPermissions() { 
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }
}