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
import android.view.*
import android.widget.*
import androidx.appcompat.app.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.*
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tvTranscript: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnListen: ImageButton
    private lateinit var scrollTranscript: ScrollView
    private lateinit var pulse1: View
    private lateinit var pulse2: View
    private lateinit var llDownload: LinearLayout

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var enEsTranslator: Translator
    private lateinit var esEnTranslator: Translator
    private var isListening = false
    private var isAiSpeaking = false
    private var isRegionalFlavorEnabled = false
    private val conversationHistory = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        tvStatus = findViewById(R.id.tvStatus)
        btnListen = findViewById(R.id.btnListen)
        scrollTranscript = findViewById(R.id.scrollTranscript)
        pulse1 = findViewById(R.id.pulse1)
        pulse2 = findViewById(R.id.pulse2)
        llDownload = findViewById(R.id.llDownload)

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            conversationHistory.clear()
            tvTranscript.text = ""
        }

        checkPermissions()
        setupTTS()
        setupTranslators()

        btnListen.setOnClickListener {
            if (isListening) stopContinuousMode() else startContinuousMode()
        }
    }

    // --- MENU LOGIC ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_regional_flavor -> {
                isRegionalFlavorEnabled = !isRegionalFlavorEnabled
                item.isChecked = isRegionalFlavorEnabled
                updateTranscriptBorder()
            }
            R.id.action_save -> saveConversation()
            R.id.action_share -> shareTranscript()
            R.id.action_clear -> { conversationHistory.clear(); tvTranscript.text = "" }
            R.id.action_reset_models -> resetModels()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateTranscriptBorder() {
        val color = if (isRegionalFlavorEnabled) Color.parseColor("#FFD700") else Color.parseColor("#444444")
        scrollTranscript.backgroundTintList = ColorStateList.valueOf(color)
    }

    // --- AI & TRANSLATION ---
    private fun setupTranslators() {
        llDownload.visibility = View.VISIBLE
        val optionsEnEs = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        val optionsEsEn = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build()
        enEsTranslator = Translation.getClient(optionsEnEs)
        esEnTranslator = Translation.getClient(optionsEsEn)

        val cond = DownloadConditions.Builder().requireWifi().build()
        enEsTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
            esEnTranslator.downloadModelIfNeeded(cond).addOnSuccessListener {
                llDownload.visibility = View.GONE
                tvStatus.text = "Status: AI Ready"
            }
        }
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
            override fun onPartialResults(p: Bundle?) {
                val text = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                tvTranscript.text = SpannableStringBuilder().append(conversationHistory).append("\nPresent: $text")
            }
            override fun onResults(r: Bundle?) {
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { detectAndTranslate(it) }
            }
            override fun onError(e: Int) { if (isListening && !isAiSpeaking) startContinuousMode() }
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
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { lang ->
            when (lang) {
                "es" -> { updatePulseColor(Color.RED); translateAndSpeak(text, esEnTranslator, Locale.US) }
                "en" -> { updatePulseColor(Color.BLUE); translateAndSpeak(text, enEsTranslator, Locale("es", "MX")) }
                else -> if (isListening) startContinuousMode()
            }
        }
    }

    private fun translateAndSpeak(text: String, trans: Translator, loc: Locale) {
        isAiSpeaking = true
        speechRecognizer?.stopListening()
        trans.translate(text).addOnSuccessListener { res ->
            val out = if (loc.language == "es" && isRegionalFlavorEnabled) applyManzanilloFlavor(res) else res
            
            // COLOR CODING
            val builder = SpannableStringBuilder()
            val inCol = if (loc.language == "es") Color.CYAN else Color.YELLOW
            val outCol = if (loc.language == "es") Color.YELLOW else Color.CYAN
            
            val inS = SpannableString("In: $text\n"); inS.setSpan(ForegroundColorSpan(inCol), 0, inS.length, 0)
            val outS = SpannableString("Out: $out\n---\n"); outS.setSpan(ForegroundColorSpan(outCol), 0, outS.length, 0)
            
            tvTranscript.append(builder.append(inS).append(outS))
            conversationHistory.append("In: $text\nOut: $out\n---\n")
            
            scrollTranscript.post { scrollTranscript.fullScroll(View.FOCUS_DOWN) }
            tts.language = loc
            tts.speak(out, TextToSpeech.QUEUE_FLUSH, null, "UTT")
        }
    }

    private fun applyManzanilloFlavor(t: String): String {
        var res = t
        val dict = mapOf("niño" to "chigüilín", "amigo" to "compa", "autobús" to "camión", "trabajo" to "chamba", "dinero" to "feria")
        for ((s, l) in dict) res = res.replace("\\b(?i)$s\\b".toRegex(), l)
        return res
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(id: String?) { isAiSpeaking = false; runOnUiThread { if (isListening) startContinuousMode() } }
                override fun onStart(id: String?) { isAiSpeaking = true }
                override fun onError(id: String?) { isAiSpeaking = false }
            })
        }
    }

    // --- ANIMATION & HELPERS ---
    private fun startPulseAnimation(color: Int) {
        val pulses = listOf(pulse1, pulse2)
        pulses.forEachIndexed { i, v ->
            v.visibility = View.VISIBLE
            v.backgroundTintList = ColorStateList.valueOf(color)
            val sX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 3f)
            val sY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 3f)
            val a = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f)
            AnimatorSet().apply {
                duration = 1500; startDelay = (i * 750).toLong()
                playTogether(sX, sY, a)
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(anim: Animator) { if (isListening) start() } })
                start()
            }
        }
    }

    private fun updatePulseColor(c: Int) {
        pulse1.backgroundTintList = ColorStateList.valueOf(c)
        pulse2.backgroundTintList = ColorStateList.valueOf(c)
    }

    private fun saveConversation() {
        val mode = if (isRegionalFlavorEnabled) "Manzanillo Mode" else "Standard"
        val header = "Log: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}\nMode: $mode\n---\n"
        val name = "Chat_${System.currentTimeMillis()}.txt"
        openFileOutput(name, Context.MODE_PRIVATE).use { it.write((header + conversationHistory.toString()).toByteArray()) }
        Toast.makeText(this, "Saved $name", Toast.LENGTH_SHORT).show()
    }

    private fun shareTranscript() {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, conversationHistory.toString()) }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun resetModels() {
        val manager = RemoteModelManager.getInstance()
        val en = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
        val es = TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()
        manager.deleteDownloadedModel(en); manager.deleteDownloadedModel(es).addOnSuccessListener { setupTranslators() }
    }

    private fun stopContinuousMode() { isListening = false; pulse1.visibility = View.INVISIBLE; pulse2.visibility = View.INVISIBLE; speechRecognizer?.destroy() }
    private fun checkPermissions() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
}