package com.example.acesssense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.acesssense.ui.theme.AcessSenseTheme
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            AcessSenseTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    GeminiTestScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun GeminiTestScreen(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var responseText by remember {
        mutableStateOf(
            "Presiona el botón para probar la conexión con Gemini."
        )
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AcessSense",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = responseText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        Button(
            enabled = !isLoading,
            onClick = {
                if (!isLoading) {
                    isLoading = true
                    responseText = "Conectando con Gemini..."

                    coroutineScope.launch {
                        try {
                            val apiKey = BuildConfig.GEMINI_API_KEY

                            if (apiKey.isBlank()) {
                                responseText =
                                    "Falta configurar GEMINI_API_KEY en local.properties."
                            } else {
                                val generativeModel = GenerativeModel(
                                    modelName = "gemini-1.5-flash",
                                    apiKey = apiKey
                                )

                                val response = generativeModel.generateContent(
                                    "Hola Gemini, responde en español con una " +
                                            "oración corta confirmando que " +
                                            "nuestra conexión funciona."
                                )

                                responseText = response.text
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Gemini devolvió una respuesta vacía."
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            responseText =
                                "Error al consultar Gemini:\n" +
                                        (e.localizedMessage ?: "Error desconocido.")
                        } finally {
                            isLoading = false
                        }
                    }
                }
            }
        ) {
            Text(
                text = if (isLoading) {
                    "Cargando..."
                } else {
                    "Probar Gemini"
                }
            )
        }
    }
}