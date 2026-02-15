package com.example.polyglotvoice

import android.Manifest
import android.animation.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.*
import android.speech.tts.*
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.mlkit.common.model.*
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tvTranscript: TextView
    private lateinit var scrollTranscript: ScrollView
    private lateinit var toolbar: MaterialToolbar
    private var isRegionalFlavorEnabled = false
    private val conversationHistory = StringBuilder()
    
    // ... (Speech, TTS, and Translator variables from previous version)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        // FORCE MENU CLICK LISTENER
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_regional_flavor -> {
                    isRegionalFlavorEnabled = !isRegionalFlavorEnabled
                    item.isChecked = isRegionalFlavorEnabled
                    updateTranscriptBorder()
                    true
                }
                R.id.action_save -> { saveConversation(); true }
                R.id.action_clear -> { clearTranscript(); true }
                else -> false
            }
        }

        tvTranscript = findViewById(R.id.tvTranscript)
        scrollTranscript = findViewById(R.id.scrollTranscript)
        
        findViewById<Button>(R.id.btnClear).setOnClickListener { clearTranscript() }
        
        // (Call your existing setup methods here)
    }

    private fun clearTranscript() {
        conversationHistory.clear()
        tvTranscript.setText("", TextView.BufferType.SPANNABLE)
    }

    private fun translateAndSpeak(text: String, trans: Translator, loc: Locale) {
        trans.translate(text).addOnSuccessListener { res ->
            val out = if (loc.language == "es" && isRegionalFlavorEnabled) applyManzanilloFlavor(res) else res
            
            // COLOR LOGIC: Explicitly creating the spans
            val inColor = if (loc.language == "es") Color.CYAN else Color.YELLOW
            val outColor = if (loc.language == "es") Color.YELLOW else Color.CYAN
            
            val combined = SpannableStringBuilder()
            
            val inSpan = SpannableString("In: $text\n")
            inSpan.setSpan(ForegroundColorSpan(inColor), 0, inSpan.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val outSpan = SpannableString("Out: $out\n---\n")
            outSpan.setSpan(ForegroundColorSpan(outColor), 0, outSpan.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            combined.append(inSpan).append(outSpan)
            
            // Append to the UI
            tvTranscript.append(combined)
            conversationHistory.append("In: $text\nOut: $out\n---\n")
            
            scrollTranscript.post { scrollTranscript.fullScroll(View.FOCUS_DOWN) }
            // (Speak logic here)
        }
    }
    
    // ... (rest of the helper methods)
}