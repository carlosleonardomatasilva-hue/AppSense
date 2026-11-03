package com.example.acesssense

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.acesssense.ui.theme.AcessSenseTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: LifecycleCameraController

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    private val handler = Handler(Looper.getMainLooper())

    private var isRecording by mutableStateOf(false)
    private var isTakingPhoto by mutableStateOf(false)

    private var photoStatus by mutableStateOf(
        "Todavía no se ha tomado una foto."
    )

    private var audioStatus by mutableStateOf(
        "Todavía no se ha grabado audio."
    )

    private val stopRecordingTask = Runnable {
        finishRecording()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        cameraController = LifecycleCameraController(this).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }

        setContent {
            AcessSenseTheme {
                CaptureScreen()
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Composable
    private fun CaptureScreen() {
        var cameraGranted by remember {
            mutableStateOf(hasPermission(Manifest.permission.CAMERA))
        }

        var microphoneGranted by remember {
            mutableStateOf(
                hasPermission(Manifest.permission.RECORD_AUDIO)
            )
        }

        var cameraReady by remember {
            mutableStateOf(false)
        }

        var cameraStatus by remember {
            mutableStateOf("Concede permiso para abrir la cámara.")
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            cameraGranted = hasPermission(Manifest.permission.CAMERA)

            microphoneGranted = hasPermission(
                Manifest.permission.RECORD_AUDIO
            )

            if (!cameraGranted) {
                cameraStatus = "No se concedió permiso de cámara."
            }

            if (!microphoneGranted) {
                audioStatus = "No se concedió permiso de micrófono."
            }
        }

        DisposableEffect(cameraGranted) {
            cameraReady = false

            if (cameraGranted) {
                try {
                    cameraController.bindToLifecycle(
                        this@MainActivity
                    )

                    cameraReady = true
                    cameraStatus = "Cámara vinculada."
                } catch (e: Exception) {
                    cameraStatus =
                        "No se pudo abrir la cámara: ${e.localizedMessage}"
                }
            }

            onDispose {
                if (cameraGranted) {
                    cameraController.unbind()
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "AcessSense",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Prueba de cámara y micrófono",
                    style = MaterialTheme.typography.titleMedium
                )

                if (!cameraGranted || !microphoneGranted) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        onClick = {
                            val pendingPermissions = mutableListOf<String>()

                            if (!cameraGranted) {
                                pendingPermissions.add(
                                    Manifest.permission.CAMERA
                                )
                            }

                            if (!microphoneGranted) {
                                pendingPermissions.add(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }

                            permissionLauncher.launch(
                                pendingPermissions.toTypedArray()
                            )
                        }
                    ) {
                        Text("Permitir cámara y micrófono")
                    }

                    Text(
                        text = "Si rechazaste los permisos varias veces, " +
                                "actívalos desde Ajustes → Aplicaciones → " +
                                "AcessSense → Permisos."
                    )
                }

                if (cameraGranted && cameraReady) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        factory = { context ->
                            PreviewView(context).apply {
                                implementationMode =
                                    PreviewView.ImplementationMode.COMPATIBLE

                                scaleType = PreviewView.ScaleType.FILL_CENTER

                                controller = cameraController
                            }
                        }
                    )
                }

                Text(text = cameraStatus)

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    enabled = cameraGranted &&
                            cameraReady &&
                            !isTakingPhoto,
                    onClick = {
                        takePhoto()
                    }
                ) {
                    Text(
                        if (isTakingPhoto) {
                            "Tomando foto..."
                        } else {
                            "Tomar foto"
                        }
                    )
                }

                Text(
                    text = photoStatus,
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider()

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    enabled = microphoneGranted && !isRecording,
                    onClick = {
                        startRecording()
                    }
                ) {
                    Text(
                        if (isRecording) {
                            "Grabando durante 5 segundos..."
                        } else {
                            "Grabar audio de 5 segundos"
                        }
                    )
                }

                if (isRecording) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = audioStatus,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Las capturas se guardan temporalmente " +
                            "en la app. Todavía no se envían a Gemini.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start
                )
            }
        }
    }

    private fun takePhoto() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            photoStatus = "Falta el permiso de cámara."
            return
        }

        val file = try {
            File.createTempFile(
                "foto_",
                ".jpg",
                cacheDir
            )
        } catch (e: Exception) {
            photoStatus =
                "No se pudo crear el archivo: ${e.localizedMessage}"
            return
        }

        isTakingPhoto = true
        photoStatus = "Capturando imagen..."

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(file)
            .build()

        try {
            cameraController.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {

                    override fun onImageSaved(
                        outputFileResults: ImageCapture.OutputFileResults
                    ) {
                        isTakingPhoto = false

                        photoStatus =
                            "Foto guardada: ${file.name}\n" +
                                    "Tamaño: ${file.length()} bytes."
                    }

                    override fun onError(
                        exception: ImageCaptureException
                    ) {
                        isTakingPhoto = false
                        file.delete()

                        photoStatus =
                            "Error al tomar foto: " +
                                    exception.localizedMessage
                    }
                }
            )
        } catch (e: Exception) {
            isTakingPhoto = false
            file.delete()

            photoStatus =
                "Error al iniciar captura: ${e.localizedMessage}"
        }
    }

    @Suppress("DEPRECATION")
    private fun startRecording() {
        if (isRecording) return

        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            audioStatus = "Falta el permiso de micrófono."
            return
        }

        var newRecorder: MediaRecorder? = null
        var newFile: File? = null

        try {
            newFile = File.createTempFile(
                "audio_",
                ".m4a",
                cacheDir
            )

            val configuredRecorder = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            newRecorder = configuredRecorder

            configuredRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(newFile.absolutePath)

                prepare()
                start()
            }

            recorder = configuredRecorder
            audioFile = newFile
            isRecording = true

            audioStatus = "Grabando... Habla cerca del micrófono."

            handler.postDelayed(
                stopRecordingTask,
                5000L
            )
        } catch (e: Exception) {
            runCatching {
                newRecorder?.release()
            }

            newFile?.delete()

            recorder = null
            audioFile = null
            isRecording = false

            audioStatus =
                "No se pudo grabar: ${e.localizedMessage}"
        }
    }

    private fun finishRecording() {
        handler.removeCallbacks(stopRecordingTask)

        val currentRecorder = recorder ?: return
        val currentFile = audioFile

        recorder = null
        audioFile = null

        try {
            currentRecorder.stop()

            audioStatus = if (
                currentFile != null && currentFile.length() > 0L
            ) {
                "Audio guardado: ${currentFile.name}\n" +
                        "Tamaño: ${currentFile.length()} bytes."
            } else {
                currentFile?.delete()
                "No se generó un archivo de audio válido."
            }
        } catch (e: Exception) {
            currentFile?.delete()

            audioStatus =
                "No se pudo finalizar el audio. " +
                        "La grabación pudo ser demasiado corta " +
                        "o el micrófono no está disponible."
        } finally {
            runCatching {
                currentRecorder.release()
            }

            isRecording = false
        }
    }

    override fun onStop() {
        finishRecording()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRecordingTask)
        super.onDestroy()
    }
}