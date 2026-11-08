# AppSense • Asistente Digital Accesible "Skyte"

**Desarrollado por:** Luis Gonzalo Guzmán Sánchez  
**Proyecto:** AppSense - Aplicación Android Multimodal y Manos Libres  
**Tecnología IA:** Google Gemini 3.5 Flash-Lite (Google AI Studio)

---

## 1. Resumen Ejecutivo y Propósito del Proyecto

**AcessSense** es una aplicación móvil diseñada bajo principios estrictos de **accesibilidad universal y control por voz sin botones**, enfocada en brindar autonomía a personas con discapacidad visual o con necesidades de uso 100% manos libres.

La aplicación elimina la necesidad de interactuar táctilmente con la pantalla, ofreciendo una experiencia conversacional continua estilo *altavoz inteligente* (tipo Alexa o Google Assistant) combinada con visión por computadora en tiempo real.

### Características Principales:
* **Operación 100% Manos Libres**: Sin botones táctiles requeridos para la interacción cotidiana. Todo el flujo es gestionado por voz.
* **Modelo Inteligente Gratuito**: Integración directa con **Gemini 3.5 Flash-Lite** utilizando la capa gratuita (*Free Tier*) de la API de Google AI Studio, garantizando costo $0 USD e inferencia multimodal ultrarrápida.
* **Identidad del Asistente "Skyte"**: Asistente con personalidad empática, cercana y concisa, configurado con voz masculina grave (`pitch = 0.85f`).
* **Detección Automática de TalkBack**: Reconoce si el lector de pantalla de Android está activo durante la primera instalación e informa al usuario.
* **Persistencia y Onboarding**: Registra y almacena el nombre del usuario (`Luis Gonzalo Guzmán Sánchez`) para dirigirse a él de forma personalizada en cada interacción.
* **Pantalla Siempre Encendida**: La pantalla permanece activa continuamente (`FLAG_KEEP_SCREEN_ON`) para permitir capturas de cámara instantáneas.
* **Cierre por Comando de Voz**: Reconoce intenciones del usuario para cerrar o salir de la aplicación mediante lenguaje natural (*"adiós"*, *"cerrar aplicación"*, *"hasta luego"*, etc.).

---

## 2. Arquitectura de la Solución

La arquitectura está desarrollada de forma reactiva utilizando **Jetpack Compose**, **Corrutinas de Kotlin**, **CameraX** y servicios nativos de Android:

```
[Inicio de la App / Surface]
        │
        ├──> ¿Primera Vez? ──> (Sí) ──> Discurso de Bienvenida por Voz
        │                                         │
        │                                         ▼
        │                               Solicitud de Permisos (Cámara / Micrófono)
        │                                         │
        │                                         ▼
        │                               Registro de Nombre por Voz
        │                                         │
        │                                         ▼
        └──> (No) ──> Saludo Inicial <────────────┘
                            │
                            ▼
                [ Bucle Continuo Manos Libres ]
                            │
                            ├──> Escucha Voz (STT: SpeechRecognizer)
                            │          │
                            │          ├──> ¿Comando de Salida? ──> (Sí) ──> Despedida y Cierre
                            │          │
                            │          └──> (No) ──> Captura Frame Cámara (480px)
                            │                              │
                            │                              ▼
                            │                    Petición Gemini 3.5 Flash-Lite
                            │                              │
                            │                              ▼
                            └──────────────────── Respuesta Hablada (TTS Masculino)
```

### 2.1. Integración con el SDK de Generative AI
Utiliza el SDK oficial de Google Generative AI para Android (`com.google.ai.client.generativeai:generativeai:0.9.0`), invocando el modelo `gemini-3.5-flash-lite`. Los fotogramas de la cámara se escalan dinámicamente a un máximo de **480px** para minimizar el tamaño de transferencia por red a ~50 KB y acelerar la respuesta a menos de 1.5 segundos.

### 2.2. Voz a Texto (STT - Speech To Text)
Utiliza la API nativa `android.speech.SpeechRecognizer` empaquetada en una corrutina suspendible (`suspendCancellableCoroutine`), detectando automáticamente cuando el usuario hace una pausa al hablar para enviar la consulta a Gemini.

### 2.3. Texto a Voz (TTS - Text To Speech)
Usa `android.speech.tts.TextToSpeech` mediante la función `speakAndWait()`, que suspende la ejecución mientras se reproduce el audio para evitar que la voz se corte o se solape con el micrófono.

---

## 3. Configuración y Requisitos del Sistema

### Permisos Requeridos (`AndroidManifest.xml`)
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

### Dependencias Principales (`build.gradle.kts`)
```kotlin
dependencies {
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    val camerax_version = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

---

## 4. Instrucciones del Sistema (System Instructions)
Gemini es configurado dinámicamente con las siguientes instrucciones de sistema:
```kotlin
systemInstruction = content {
    text("Eres Skyte, un asistente personal digital masculino amigable que sirve a $userName. Responde a $userName de forma coherente, cercana, natural y muy concisa (máximo 15 a 20 palabras). Mantén el hilo de la conversación. Si la pregunta se refiere a lo que ve la cámara o qué hay al frente, utiliza la imagen para describir objetivamente los elementos.")
}
```

---

## 5. Guía de Seguridad y Mantenimiento
* **Optimización de Cuota**: Al utilizar imágenes livianas (480px) y respuestas concisas (15-20 palabras), la aplicación consume aproximadamente 3 a 6 peticiones por minuto, operando perfectamente dentro de la capa gratuita de Google AI Studio (15 RPM / 1,500 RPD).
