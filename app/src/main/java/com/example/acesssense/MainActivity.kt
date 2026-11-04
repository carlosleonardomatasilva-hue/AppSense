package com.example.acesssense

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.acesssense.ui.theme.AcessSenseTheme
import com.example.acesssense.ui.theme.Green400
import com.example.acesssense.ui.theme.Green50
import com.example.acesssense.ui.theme.Green600
import com.example.acesssense.ui.theme.Slate100
import com.example.acesssense.ui.theme.Slate300
import com.example.acesssense.ui.theme.Slate50
import com.example.acesssense.ui.theme.Slate700
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantener la pantalla encendida continuamente mientras se ejecuta la app
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()

        setContent {
            AcessSenseTheme {
                PreviewScreen()
            }
        }
    }
}

object UserPreferences {
    private const val PREFS_NAME = "skyte_prefs"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_USER_NAME = "user_name"

    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    fun saveUserName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }
}

fun isTalkBackEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    if (accessibilityManager != null && accessibilityManager.isEnabled) {
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
        return enabledServices.any { service ->
            service.id.contains("talkback", ignoreCase = true) ||
            service.id.contains("marvin", ignoreCase = true) ||
            service.id.contains("screenreader", ignoreCase = true)
        }
    }
    return false
}

fun isExitCommand(userSpeech: String): Boolean {
    val speech = userSpeech.lowercase(Locale.getDefault()).trim()
    val exitKeywords = listOf(
        "salir", "cerrar", "adiós", "adios", "hasta luego", "chao", "chau", "apagar",
        "cerrar aplicación", "cerrar la aplicación", "salir de la aplicación",
        "cerrar app", "salir de la app", "nos vemos"
    )
    return exitKeywords.any { speech.contains(it) }
}

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    var isReady = mutableStateOf(false)
        private set

    private val readyDeferred = CompletableDeferred<Boolean>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("es-ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }

            // Configurar voz varonil / masculina si está disponible en el dispositivo
            try {
                val voices = tts?.voices
                if (!voices.isNullOrEmpty()) {
                    val maleSpanishVoice = voices.firstOrNull { voice ->
                        voice.locale.language == "es" && (
                            voice.name.contains("male", ignoreCase = true) ||
                            voice.name.contains("masculin", ignoreCase = true) ||
                            voice.name.contains("es-es-x-esd", ignoreCase = true) ||
                            voice.name.contains("es-es-x-esf", ignoreCase = true) ||
                            voice.name.contains("es-mx-x-sfd", ignoreCase = true) ||
                            voice.name.contains("es-us-x-sfd", ignoreCase = true)
                        )
                    }
                    if (maleSpanishVoice != null) {
                        tts?.voice = maleSpanishVoice
                    }
                }
            } catch (e: Exception) {
                Log.w("TTS", "No se pudo cambiar la voz específica", e)
            }

            // Tono varonil grave y velocidad fluida
            tts?.setPitch(0.85f)
            tts?.setSpeechRate(1.25f)

            isReady.value = true
            readyDeferred.complete(true)
        } else {
            Log.e("TTS", "Inicialización de TextToSpeech fallida")
            readyDeferred.complete(false)
        }
    }

    suspend fun awaitReady(): Boolean {
        if (isReady.value) return true
        return try {
            withTimeout(3000L) {
                readyDeferred.await()
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun speakAndWait(text: String) {
        if (!awaitReady() || text.isBlank()) return
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val utteranceId = "Utterance_${System.currentTimeMillis()}"
                val listener = object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
                tts?.setOnUtteranceProgressListener(listener)
                val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (speakResult != TextToSpeech.SUCCESS) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

class SttHelper(context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    suspend fun listenForSpeech(): String? = withContext(Dispatchers.Main) {
        val recognizer = speechRecognizer ?: return@withContext null

        suspendCancellableCoroutine { continuation ->
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.w("STT", "SpeechRecognizer error code: $error")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (continuation.isActive) {
                        continuation.resume(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer.setRecognitionListener(listener)
            recognizer.startListening(intent)

            continuation.invokeOnCancellation {
                try {
                    recognizer.stopListening()
                } catch (e: Exception) {
                    Log.w("STT", "Error al detener SpeechRecognizer", e)
                }
            }
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w("STT", "Error destruyendo SpeechRecognizer", e)
        }
    }
}

fun Bitmap.scaleToMaxDimension(maxDimension: Int = 480): Bitmap {
    val width = this.width
    val height = this.height
    if (width <= maxDimension && height <= maxDimension) return this
    val ratio = width.toFloat() / height.toFloat()
    val targetWidth: Int
    val targetHeight: Int
    if (width >= height) {
        targetWidth = maxDimension
        targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
    } else {
        targetHeight = maxDimension
        targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
    }
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

fun extractCleanName(rawSpeech: String): String {
    var text = rawSpeech.trim()
    val lower = text.lowercase(Locale.getDefault())
    val prefixes = listOf("me llamo", "mi nombre es", "soy", "me dicen", "llama me")
    for (prefix in prefixes) {
        if (lower.startsWith(prefix)) {
            text = text.substring(prefix.length).trim()
            break
        }
    }
    return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        .ifBlank { "Amigo" }
}

private val conversationHistory = mutableListOf<String>()

private suspend fun generateInitialGreeting(userName: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3.5-flash-lite",
                apiKey = BuildConfig.GEMINI_API_KEY,
                systemInstruction = content {
                    text("Eres Skyte, un asistente personal digital masculino amigable que utiliza tecnología de Gemini para servir a $userName.")
                }
            )
            val response = generativeModel.generateContent(
                content { text("Saluda a $userName amigablemente al iniciar la aplicación en menos de 15 palabras.") }
            )
            response.text?.trim() ?: "¡Hola $userName! Soy Skyte, tu asistente digital. ¿En qué te puedo ayudar hoy?"
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error generando saludo inicial", e)
            "¡Hola $userName! Soy Skyte, tu asistente digital. ¿En qué te puedo ayudar hoy?"
        }
    }
}

private suspend fun processUserQueryWithGemini(userQuery: String, bitmap: Bitmap?, userName: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3.5-flash-lite",
                apiKey = BuildConfig.GEMINI_API_KEY,
                systemInstruction = content {
                    text("Eres Skyte, un asistente personal digital masculino amigable que sirve a $userName. Responde a $userName de forma coherente, cercana, natural y muy concisa (máximo 15 a 20 palabras). Mantén el hilo de la conversación. Si la pregunta se refiere a lo que ve la cámara o qué hay al frente, utiliza la imagen para describir objetivamente los elementos.")
                }
            )

            // Incluir historial de conversación para coherencia contextual continua
            val recentContext = conversationHistory.takeLast(6).joinToString("\n")
            val fullPrompt = if (recentContext.isNotBlank()) {
                "Historial reciente:\n$recentContext\n\n$userName dice: $userQuery"
            } else {
                "$userName dice: $userQuery"
            }

            val inputContent = content {
                if (bitmap != null) {
                    image(bitmap)
                }
                text(fullPrompt)
            }
            val response = generativeModel.generateContent(inputContent)
            val resultText = response.text?.trim() ?: "Entendido."

            // Actualizar memoria conversacional
            conversationHistory.add("$userName: $userQuery")
            conversationHistory.add("Skyte: $resultText")
            if (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }

            resultText
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error al procesar consulta con Gemini 3.5 Flash-Lite", e)
            "Disculpa $userName, ocurrió un problema de conexión."
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onPreviewViewCreated: (PreviewView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                onPreviewViewCreated(this)
            }
        },
        modifier = modifier,
        update = { previewView ->
            onPreviewViewCreated(previewView)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Error al vincular la cámara", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )

    DisposableEffect(lifecycleOwner) {
        onDispose {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            }, ContextCompat.getMainExecutor(context))
        }
    }
}

@Composable
fun PreviewScreen() {
    val context = LocalContext.current
    var dynamicContent by remember { mutableStateOf("Iniciando Asistente Skyte...") }

    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }

    val ttsHelper = remember(context) { TtsHelper(context) }
    val sttHelper = remember(context) { SttHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
            sttHelper.destroy()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    // 1. Flujo de inicio: Esperar inicialización de TTS y pronunciar la bienvenida ANTES de pedir permisos
    LaunchedEffect(Unit) {
        ttsHelper.awaitReady()

        val isFirstLaunch = UserPreferences.isFirstLaunch(context)

        if (isFirstLaunch) {
            val talkBackNotice = if (isTalkBackEnabled(context)) {
                " He detectado que tienes activado el lector de pantalla TalkBack. La aplicación está totalmente adaptada para funcionar contigo."
            } else ""

            // Pronunciar el mensaje de bienvenida completo PRIMERO
            val welcomeSpeech = "¡Hola! Bienvenido. Soy tu asistente digital Skyte. Utilizo la tecnología de Gemini para funcionar y estoy totalmente para servirte.$talkBackNotice Como primer paso, necesito ciertos permisos para funcionar. Si me los concedes, estaré al cien por ciento para apoyarte."
            dynamicContent = welcomeSpeech
            ttsHelper.speakAndWait(welcomeSpeech)

            // Solicitar permisos ÚNICAMENTE LUEGO de terminar el mensaje de bienvenida hablado
            if (!hasCameraPermission || !hasAudioPermission) {
                dynamicContent = "Solicitando permisos para activar a Skyte..."
                permissionsLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        } else if (!hasCameraPermission || !hasAudioPermission) {
            permissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    val hasAllPermissions = hasCameraPermission && hasAudioPermission

    val previewBorderColor by animateColorAsState(
        targetValue = if (hasAllPermissions) Green400 else Slate300,
        animationSpec = tween(durationMillis = 300)
    )
    val previewBackgroundColor by animateColorAsState(
        targetValue = if (hasAllPermissions) Green50 else Slate50,
        animationSpec = tween(durationMillis = 300)
    )

    // 2. Flujo cuando los permisos están concedidos
    LaunchedEffect(hasAllPermissions) {
        if (!hasAllPermissions) return@LaunchedEffect

        ttsHelper.awaitReady()

        val isFirstLaunch = UserPreferences.isFirstLaunch(context)

        if (isFirstLaunch) {
            // Solicitar el nombre del usuario
            val namePrompt = "¡Excelente! Ahora, por favor, dime cuál es tu nombre para saber cómo llamarte."
            dynamicContent = namePrompt
            ttsHelper.speakAndWait(namePrompt)

            var capturedName = ""
            while (capturedName.isBlank() && isActive) {
                dynamicContent = "Escuchando tu nombre..."
                val spokenText = sttHelper.listenForSpeech()
                if (!spokenText.isNullOrBlank()) {
                    capturedName = extractCleanName(spokenText)
                } else {
                    val retryPrompt = "Por favor, dime tu nombre para continuar."
                    dynamicContent = retryPrompt
                    ttsHelper.speakAndWait(retryPrompt)
                }
            }

            if (capturedName.isNotBlank()) {
                UserPreferences.saveUserName(context, capturedName)
                UserPreferences.setFirstLaunchCompleted(context)

                val confirmationSpeech = "¡Mucho gusto, $capturedName! He guardado tu nombre. A partir de ahora seré tu asistente digital. ¿En qué te puedo apoyar hoy?"
                dynamicContent = confirmationSpeech
                ttsHelper.speakAndWait(confirmationSpeech)
            }
        } else {
            // Lanzamientos Posteriores: Cargar nombre guardado y dar saludo inicial
            val savedName = UserPreferences.getUserName(context).ifBlank { "Amigo" }
            dynamicContent = "Generando saludo inicial..."
            val initialGreeting = generateInitialGreeting(savedName)
            dynamicContent = initialGreeting
            ttsHelper.speakAndWait(initialGreeting)
        }

        // Bucle Continuo Manos Libres (Estilo Alexa)
        val currentUserName = UserPreferences.getUserName(context).ifBlank { "Amigo" }

        while (isActive) {
            dynamicContent = "Escuchando a $currentUserName..."
            val userSpeech = sttHelper.listenForSpeech()

            if (!userSpeech.isNullOrBlank() && isActive) {
                // Comando por voz para cerrar la aplicación
                if (isExitCommand(userSpeech)) {
                    val exitSpeech = "Hasta luego $currentUserName, estaré listo para cuando me necesites. ¡Que tengas un excelente día!"
                    dynamicContent = exitSpeech
                    ttsHelper.speakAndWait(exitSpeech)
                    (context as? ComponentActivity)?.finishAndRemoveTask()
                    return@LaunchedEffect
                }

                dynamicContent = "$currentUserName: \"$userSpeech\""

                val currentBitmap = previewViewInstance?.bitmap?.scaleToMaxDimension(480)

                dynamicContent = "Pensando..."
                val responseText = processUserQueryWithGemini(userSpeech, currentBitmap, currentUserName)

                if (responseText.isNotBlank() && isActive) {
                    dynamicContent = responseText
                    ttsHelper.speakAndWait(responseText)
                }
            } else {
                delay(300L)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = Slate100
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Encabezado Accesible sin botones
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate50)
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AcessSense • Skyte Asistente Digital",
                            color = Slate700,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Recuadro Principal de Cámara
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .border(
                                border = BorderStroke(3.dp, previewBorderColor),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(previewBackgroundColor, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                onPreviewViewCreated = { view ->
                                    previewViewInstance = view
                                }
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Se requieren permisos de cámara y micrófono para activar a Skyte",
                                    color = Slate700,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Button(
                                    onClick = {
                                        permissionsLauncher.launch(
                                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Green600)
                                ) {
                                    Text("Conceder Permisos", color = Color.White)
                                }
                            }
                        }
                    }

                    // Panel Inferior Dinámico de Conversación y Respuestas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate50)
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dynamicContent,
                            color = Slate700,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
