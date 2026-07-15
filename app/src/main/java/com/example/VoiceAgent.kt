package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// --- Common Data Classes ---
@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

// --- Retrofit Setup ---
interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

// --- Agent Logic ---
class VoiceAgentViewModel(
    application: Application
) : AndroidViewModel(application), TextToSpeech.OnInitListener, RecognitionListener {

    private val context: Context = application.applicationContext

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false

    private val conversationHistory = mutableListOf<Content>()
    
    private val _personaPrompt = MutableStateFlow("Du bist ein professioneller, KI-gesteuerter Voice-Agent der Firma Büchner Cognitive. Du rufst den Kunden (Herr Meyer) an, um über Prozessautomatisierung zu sprechen. Antworte kurz, prägnant und auf Deutsch, als wärst du in einem Telefongespräch. Nutze keine Emojis oder Formatierungen.")
    val personaPrompt: StateFlow<String> = _personaPrompt.asStateFlow()

    fun updatePersonaPrompt(newPrompt: String) {
        _personaPrompt.value = newPrompt
    }

    init {
        textToSpeech = TextToSpeech(context, this)
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        } else {
            addLog("[ERROR] Speech Recognition nicht verfügbar auf diesem Gerät.")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.GERMANY
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Speaking started
                }
                override fun onDone(utteranceId: String?) {
                    // Speaking ended, start listening again
                    if (_agentState.value == AgentState.ACTIVE) {
                        viewModelScope.launch(Dispatchers.Main) {
                            startListening()
                        }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    addLog("[ERROR] TTS Fehler.")
                }
            })
            addLog("[SYSTEM] Text-to-Speech initialisiert.")
        } else {
            addLog("[ERROR] Fehler bei Text-to-Speech Initialisierung.")
        }
    }

    fun startCall() {
        if (_agentState.value != AgentState.IDLE) return
        
        conversationHistory.clear()
        _logs.value = emptyList()
        _agentState.value = AgentState.DIALING
        addLog("[SYSTEM] Wähle Nummer...")
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (_agentState.value != AgentState.DIALING) return@launch
            _agentState.value = AgentState.CONNECTING
            addLog("[SYSTEM] Klingelt...")
            kotlinx.coroutines.delay(2000)
            if (_agentState.value != AgentState.CONNECTING) return@launch
            
            _agentState.value = AgentState.ACTIVE
            addLog("[SYSTEM] VERBUNDEN.")
            
            val initialGreeting = "Guten Tag Herr Meyer, hier spricht der digitale Assistent von Büchner Cognitive. Haben Sie einen Moment Zeit?"
            addLog("KI: $initialGreeting")
            conversationHistory.add(Content(role = "model", parts = listOf(Part(text = initialGreeting))))
            
            speak(initialGreeting)
        }
    }

    fun endCall() {
        _agentState.value = AgentState.IDLE
        stopListening()
        textToSpeech?.stop()
        addLog("[SYSTEM] Anruf beendet.")
    }

    private fun speak(text: String) {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AgentUtterance")
    }

    private fun startListening() {
        if (!isListening && _agentState.value == AgentState.ACTIVE) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                addLog("[SYSTEM] (Hört zu...)")
            } catch (e: Exception) {
                addLog("[ERROR] Fehler beim Starten des Mikrofons: ${e.message}")
            }
        }
    }

    private fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    private fun addLog(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val current = _logs.value.toMutableList()
            current.add(message)
            _logs.value = current
        }
    }

    // --- RecognitionListener Implementation ---
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        isListening = false
    }
    
    override fun onError(error: Int) {
        isListening = false
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT && error != SpeechRecognizer.ERROR_CLIENT) {
            addLog("[ERROR] Spracherkennung Fehlercode: $error")
        }
        // Retry listening if still active
        if (_agentState.value == AgentState.ACTIVE) {
            viewModelScope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(1000)
                startListening()
            }
        }
    }

    fun processTextInput(text: String) {
        if (_agentState.value != AgentState.ACTIVE) return
        
        // Stop listening temporarily to handle text input
        stopListening()
        
        addLog("KUNDE (Text): $text")
        conversationHistory.add(Content(role = "user", parts = listOf(Part(text = text))))
        
        callGeminiApi()
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull()
        if (!recognizedText.isNullOrEmpty()) {
            addLog("KUNDE: $recognizedText")
            conversationHistory.add(Content(role = "user", parts = listOf(Part(text = recognizedText))))
            
            callGeminiApi()
        } else {
            // Nothing recognized, try again
            if (_agentState.value == AgentState.ACTIVE) {
                startListening()
            }
        }
    }

    private fun callGeminiApi() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    addLog("[ERROR] Bitte GEMINI_API_KEY im Secrets Panel konfigurieren.")
                    return@launch
                }
                
                val request = GenerateContentRequest(
                    contents = conversationHistory,
                    systemInstruction = Content(role = "system", parts = listOf(Part(_personaPrompt.value)))
                )
                
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val reply = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Keine Antwort."
                
                conversationHistory.add(Content(role = "model", parts = listOf(Part(text = reply))))
                addLog("KI: $reply")
                
                withContext(Dispatchers.Main) {
                    speak(reply)
                }
                
            } catch (e: Exception) {
                addLog("[ERROR] API Fehler: ${e.message}")
                withContext(Dispatchers.Main) {
                    speak("Entschuldigung, ich habe gerade Verbindungsprobleme.")
                }
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
