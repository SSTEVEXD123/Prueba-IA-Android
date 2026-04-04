# ⚡ Jarvis App — Asistente Personal con IA Local para Android

> **IA completamente offline en tu bolsillo.** LLM + Stable Diffusion + Moondream 2, todo ejecutándose directamente en tu Android, sin internet, sin servidores, sin privacidad comprometida.

---

## 📸 Características

| Función | Descripción |
|---|---|
| 🧠 **Chat IA Local** | Conversaciones completas con LLMs GGUF vía llama.cpp |
| 🎨 **Generación de Imágenes** | Stable Diffusion local (SD Turbo, LCM DreamShaper) |
| 👁️ **Visión Artificial** | Moondream 2 para análisis y descripción de imágenes |
| 💬 **Reconocimiento de Voz** | Entrada por micrófono con SpeechRecognizer nativo |
| 📊 **Dashboard** | CPU, RAM y almacenamiento en tiempo real |
| 📄 **Generador de PDFs** | Con estilos, Markdown y diseño Jarvis |
| 📁 **Gestor de Archivos** | CRUD completo con editor Markdown integrado |
| 🔌 **APIs Públicas** | Consulta servicios externos con resúmenes de IA |
| 🗃️ **SQLite Local** | Todo el historial y datos en tu dispositivo |

---

## 📋 Requisitos

| Componente | Mínimo | Recomendado |
|---|---|---|
| Android | API 26 (8.0) | API 33+ (13) |
| RAM del dispositivo | 4 GB | 6–8 GB |
| Almacenamiento libre | 3 GB | 6–10 GB |
| Arquitectura | arm64-v8a | arm64-v8a |
| Android NDK | r25c | r26d |
| Android Studio | Hedgehog | Iguana+ |
| JDK | 17 | 17 |

---

## 🚀 Instalación Rápida

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/JarvisApp.git
cd JarvisApp
```

### 2. Configurar llama.cpp (obligatorio)

```bash
chmod +x scripts/setup_llama_cpp.sh
bash scripts/setup_llama_cpp.sh
```

Esto clona automáticamente `llama.cpp` y `stable-diffusion.cpp` dentro de `app/src/main/cpp/`.

### 3. Abrir en Android Studio

```
File → Open → Selecciona la carpeta JarvisApp/
Espera la sincronización de Gradle (2–5 min)
```

### 4. Compilar

```
Build → Make Project   (o Ctrl+F9)
Run → Run 'app'        (o Shift+F10)
```

### 5. Descargar modelos

**Opción A — Desde la app** (recomendado):
1. Abre Jarvis App
2. Ve a **Modelos** (ícono robot)
3. Toca **Descargar** en el modelo deseado
4. Espera la descarga en background
5. Toca **Activar**

**Opción B — Script ADB** (para múltiples modelos):
```bash
# Con tu Android conectado por USB con depuración activa:
bash scripts/download_models.sh
```

---

## 🤖 Modelos Disponibles

### Modelos de Lenguaje (LLM)

| Modelo | Parámetros | Cuantización | Tamaño | RAM necesaria |
|---|---|---|---|---|
| **Phi-3 Mini** | 3.8B | Q4_K_M | 2.4 GB | ~3 GB |
| **Gemma 2** | 2B | Q5_K_M | 1.9 GB | ~2.5 GB |
| **Llama 3.2** | 3B | Q4_K_M | 2.1 GB | ~3 GB |
| **Qwen 2.5** | 1.5B | Q6_K | 1.1 GB | ~1.8 GB |
| **SmolLM2** | 360M | Q8_0 | 400 MB | ~700 MB |

### Modelos de Imagen

| Modelo | Tipo | Tamaño | Velocidad |
|---|---|---|---|
| **SD Turbo** | Texto→Imagen | 1.6 GB | Muy rápido (4 pasos) |
| **LCM DreamShaper** | Texto→Imagen | 2.0 GB | Rápido (8 pasos) |

### Modelos de Visión

| Modelo | Capacidades | Tamaño |
|---|---|---|
| **Moondream 2** | Descripción, VQA, OCR | 1.8 GB |

---

## 📁 Estructura del Proyecto

```
JarvisApp/
├── app/
│   └── src/main/
│       ├── cpp/                         # Código nativo C++
│       │   ├── CMakeLists.txt           # Build system NDK
│       │   ├── jarvis_jni.cpp           # Puente JNI
│       │   ├── inference_engine.cpp/h   # Motor llama.cpp
│       │   ├── model_manager.cpp/h      # Gestión de modelos
│       │   ├── llama.cpp/               # (git clone automático)
│       │   └── stable-diffusion.cpp/    # (git clone automático)
│       │
│       └── java/com/jarvis/app/
│           ├── ai/
│           │   ├── llm/                 # LlamaEngine (inferencia texto)
│           │   ├── image/               # StableDiffusionEngine
│           │   └── vision/              # MoondreamEngine
│           ├── data/
│           │   └── database/            # Room: entidades, DAOs, DB
│           ├── ui/
│           │   ├── chat/                # ChatFragment + ViewModel + Adapter
│           │   ├── dashboard/           # DashboardFragment
│           │   ├── models/              # ModelsFragment + ViewModel
│           │   ├── files/               # FilesFragment + Editor
│           │   └── image/               # ImageGenFragment
│           ├── utils/
│           │   ├── JarvisConfig.kt      # ⭐ Configuración + PROMPT JARVIS
│           │   ├── PdfGenerator.kt      # Generación de PDFs
│           │   └── FileManager.kt       # CRUD de archivos
│           └── services/
│               └── ModelDownloadService.kt  # Descarga en background
│
├── scripts/
│   ├── setup_llama_cpp.sh   # Clona dependencias C++
│   └── download_models.sh   # Descarga modelos vía ADB
│
└── README.md
```

### Carpeta en el Dispositivo

Una vez instalada, la app crea automáticamente:

```
/sdcard/JarvisApp/
├── models/
│   ├── llm/        → Modelos GGUF de texto (Phi-3, Llama, etc.)
│   ├── image/      → Modelos Stable Diffusion
│   └── vision/     → Moondream 2
├── generated_images/   → Imágenes creadas por SD
├── generated_pdfs/     → PDFs exportados
├── files/              → Archivos del usuario
├── chat_exports/       → Conversaciones exportadas
└── README.txt
```

---

## 🧠 Prompt del Sistema (Jarvis)

El prompt interno se configura en `JarvisConfig.kt`:

```kotlin
const val SYSTEM_PROMPT = """
Eres Jarvis, un asistente de inteligencia artificial avanzado...
- Responde SIEMPRE en español
- Respuestas largas, completas y estructuradas
- Incluye: explicación → pasos → ejemplos → alternativas
- Usa Markdown: negritas, listas, código, tablas
...
"""
```

Para personalizar el comportamiento, edita `JarvisConfig.SYSTEM_PROMPT`.

---

## ⚙️ Configuración Avanzada

### Parámetros de Generación

En `JarvisConfig.kt`:

```kotlin
const val DEFAULT_MAX_TOKENS = 2048    // Longitud máxima de respuesta
const val DEFAULT_TEMPERATURE = 0.7f  // Creatividad (0.0–1.0)
const val DEFAULT_TOP_P = 0.9f         // Nucleus sampling
const val DEFAULT_REPEAT_PENALTY = 1.1f
const val DEFAULT_N_CTX = 4096        // Ventana de contexto
const val DEFAULT_N_THREADS = 4       // Hilos de CPU
```

### Modelos Personalizados

Para usar cualquier modelo GGUF compatible:
1. Copia el archivo `.gguf` a `/sdcard/JarvisApp/models/llm/`
2. Jarvis App → Modelos → Importar .gguf
3. Actívalo y listo

---

## 🔧 Solución de Problemas

| Problema | Solución |
|---|---|
| `UnsatisfiedLinkError` al arrancar | Ejecuta `setup_llama_cpp.sh` y recompila |
| Modelo no carga / crash | Comprueba que tienes RAM suficiente y que el .gguf no está corrupto |
| Generación muy lenta | Usa un modelo más pequeño (SmolLM2 o Qwen 1.5B) |
| SD no genera imágenes | Verifica que el modelo image está en `/models/image/` y activado |
| Sin permiso de almacenamiento | Ajustes del sistema → Apps → Jarvis → Permisos → Almacenamiento |
| Gradle sync falla | `File → Invalidate Caches → Restart` en Android Studio |

---

## 📊 Rendimiento Aproximado (tokens/segundo)

| Dispositivo | Phi-3 Mini Q4 | Qwen 1.5B Q6 | SmolLM2 Q8 |
|---|---|---|---|
| Snapdragon 8 Gen 2 | ~12 t/s | ~22 t/s | ~45 t/s |
| Snapdragon 888 | ~8 t/s | ~15 t/s | ~30 t/s |
| Dimensity 9200 | ~10 t/s | ~18 t/s | ~38 t/s |
| Tensor G3 | ~9 t/s | ~17 t/s | ~35 t/s |

---

## 🛡️ Privacidad

- **100% offline** — Ningún dato sale de tu dispositivo
- **Sin telemetría** — No hay analytics ni tracking
- **Sin cuentas** — No requiere registro ni login
- **Sin nube** — Todo se guarda en `/sdcard/JarvisApp/`
- **Código abierto** — Auditable en su totalidad

---

## 📄 Licencia

MIT License — Libre para uso personal, educativo y comercial.

---

## 🤝 Contribuir

1. Fork del repositorio
2. Crea una rama: `git checkout -b feature/mi-mejora`
3. Commit: `git commit -m 'Añade función X'`
4. Push: `git push origin feature/mi-mejora`
5. Abre un Pull Request

---

<p align="center">
⚡ <strong>Jarvis App</strong> — Hecho con ❤️ para la comunidad Android
</p>
