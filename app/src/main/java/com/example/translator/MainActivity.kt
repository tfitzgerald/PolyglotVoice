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

    // Hardware Colors (Explicit Hex)
    private val CYAN = 0xFF00FFFF.toInt()
    private val YELLOW = 0xFFFFFF00.toInt()
    private val GRAY = 0xFF555555.toInt()

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
			val out = if (loc.language == "es" && isRegionalFlavorEnabled) applyRegionalFlavor(res) else res
			
			// Use high-saturation Hex colors for better visibility
			val colorCyan = Color.parseColor("#00FFFF")   // Pure Cyan
			val colorYellow = Color.parseColor("#FFFF00") // Pure Yellow

			// Identify colors based on the language of the INPUT text
			// If loc is US, the AI is speaking English (Output), meaning Input was Spanish (Yellow)
			val inColor = if (loc.language == "en") YELLOW else CYAN
			val outColor = if (loc.language == "en") CYAN else YELLOW

			// FORCE PULSE COLOR IMMEDIATELY
			runOnUiThread { startPulse(inColor) } 

			val sIn = SpannableString("In: $text\n")
			// SPAN_POINT_MARK is harder for the system to ignore
			sIn.setSpan(ForegroundColorSpan(inColor), 0, sIn.length, Spannable.SPAN_POINT_MARK)
			
			val sOut = SpannableString("Out: $out\n")
			sOut.setSpan(ForegroundColorSpan(outColor), 0, sOut.length, Spannable.SPAN_POINT_MARK)

			val div = SpannableString("────────────────\n")
			div.setSpan(ForegroundColorSpan(Color.DKGRAY), 0, div.length, Spannable.SPAN_POINT_MARK)

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

    private fun applyRegionalFlavor(t: String): String {
        var r = t
        val dictionary = mapOf("niño" to "chigüilín", "amigo" to "compa", "trabajo" to "chamba", "dinero" to "feria")
        for ((k, v) in dictionary) r = r.replace("(?i)\\b$k\\b".toRegex(), v)
        return r
    }

    private fun saveAndShare() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val content = tvTranscript.text.toString()
        try {
            val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "PolyglotLog_$timestamp.txt")
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(intent, "Share/DM Transcript"))
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
        startPulse(Color.LTGRAY)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) { r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { detectLanguage(it) } }
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

    private fun detectLanguage(text: String) {
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { lang ->
            if (lang == "es") translateAndSpeak(text, esEnTranslator, Locale.US)
            else translateAndSpeak(text, enEsTranslator, Locale("es", "MX"))
        }
    }

	private fun startPulse(color: Int) {
		runOnUiThread {
			listOf(pulse1, pulse2).forEach { v ->
				v.visibility = View.VISIBLE
				// This is the hardware command to change the color of the circle
				v.backgroundTintList = ColorStateList.valueOf(color) 
				
				val sX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 4f)
				val sY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 4f)
				val alpha = ObjectAnimator.ofFloat(v, "alpha", 0.8f, 0f)
				
				AnimatorSet().apply {
					duration = 1200
					playTogether(sX, sY, alpha)
					addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (isListening) start()
						}
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