class MainActivity : AppCompatActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var enEsTranslator: Translator
    private lateinit var esEnTranslator: Translator
    private val modelManager = RemoteModelManager.getInstance()

    private var isListening = false
    private var isAiSpeaking = false
    private var conversationHistory = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupTranslators()
        setupTTS()

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
                tvStatus.text = "Status: AI Ready (Offline)"
            }
        }
    }

    private fun startContinuousMode() {
        if (isAiSpeaking) return
        isListening = true
        startPulseAnimation(Color.LTGRAY) // Default waiting color

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Real-time display
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") 
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                tvTranscript.text = "${conversationHistory}\nPresent: $text"
            }

            override fun onResults(results: Bundle?) {
                val finalResult = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                finalResult?.let { detectAndTranslate(it) }
            }

            override fun onError(p0: Int) { if (isListening && !isAiSpeaking) startContinuousMode() }
            // ... other callbacks
        })
        speechRecognizer.startListening(intent)
    }

    private fun detectAndTranslate(text: String) {
        LanguageIdentification.getClient().identifyLanguage(text).addOnSuccessListener { langCode ->
            when (langCode) {
                "es" -> {
                    updatePulseColor(Color.RED) // Spanish turn
                    translateAndSpeak(text, esEnTranslator, Locale.US)
                }
                "en" -> {
                    updatePulseColor(Color.BLUE) // English turn
                    translateAndSpeak(text, enEsTranslator, Locale("es", "ES"))
                }
            }
        }
    }

    private fun translateAndSpeak(text: String, translator: Translator, locale: Locale) {
        isAiSpeaking = true
        speechRecognizer.stopListening()
        
        translator.translate(text).addOnSuccessListener { translated ->
            conversationHistory.append("\nIn: $text\nOut: $translated\n---\n")
            tvTranscript.text = conversationHistory.toString()
            
            tts.language = locale
            tts.speak(translated, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")
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

    // SAVING CONVERSATION
    private fun saveConversation() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Chat_$timestamp.txt"
        openFileOutput(filename, Context.MODE_PRIVATE).use { 
            it.write(conversationHistory.toString().toByteArray()) 
        }
        Toast.makeText(this, "Saved as $filename", Toast.LENGTH_SHORT).show()
    }

    // MODEL RESET (RemoteModelManager)
    private fun deleteModels() {
        val spanishModel = TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()
        modelManager.deleteDownloadedModel(spanishModel).addOnSuccessListener {
            Toast.makeText(this, "Spanish Model Deleted", Toast.LENGTH_SHORT).show()
            setupTranslators() // Trigger re-download
        }
    }
	
	private fun startPulseAnimation(color: Int) {
		val pulses = listOf(findViewById<View>(R.id.pulse1), findViewById<View>(R.id.pulse2))
		pulses.forEachIndexed { index, view ->
			view.visibility = View.VISIBLE
			view.backgroundTintList = ColorStateList.valueOf(color)
			
			val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 3f)
			val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 3f)
			val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)

			AnimatorSet().apply {
				duration = 1500
				startDelay = (index * 750).toLong()
				playTogether(scaleX, scaleY, alpha)
				addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (isListening) start() // Loop if still listening
					}
				})
				start()
			}
		}
	}
	// 1. Tell Android to inflate your menu
	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.main_menu, menu)
		return true
	}

	// 2. Handle the clicks for each item
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.action_share -> {
				shareTranscript()
				return true
			}
			R.id.action_save -> {
				saveConversation() // Function from previous step
				return true
			}
			R.id.action_clear -> {
				conversationHistory.clear()
				tvTranscript.text = ""
				Toast.makeText(this, "History Cleared", Toast.LENGTH_SHORT).show()
				return true
			}
			R.id.action_reset_models -> {
				showResetConfirmation()
				return true
			}
		}
		return super.onOptionsItemSelected(item)
	}

	// 3. Logic for the Share Button
	private fun shareTranscript() {
		val sendIntent = Intent().apply {
			action = Intent.ACTION_SEND
			putExtra(Intent.EXTRA_TEXT, conversationHistory.toString())
			type = "text/plain"
		}
		val shareIntent = Intent.createChooser(sendIntent, "Share Conversation Via:")
		startActivity(shareIntent)
	}

	// 4. Logic for the Model Reset (using RemoteModelManager)
	private fun showResetConfirmation() {
		AlertDialog.Builder(this)
			.setTitle("Reset AI Models")
			.setMessage("This will delete your offline models and re-download them. Proceed?")
			.setPositiveButton("Reset") { _, _ ->
				val englishModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
				val spanishModel = TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()
				
				modelManager.deleteDownloadedModel(englishModel)
				modelManager.deleteDownloadedModel(spanishModel)
					.addOnSuccessListener {
						Toast.makeText(this, "Models deleted. Restarting download...", Toast.LENGTH_LONG).show()
						setupTranslators() // This triggers the progress bar and re-download
					}
			}
			.setNegativeButton("Cancel", null)
			.show()
	}
	
}