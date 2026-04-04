# 🛠️ Guía de Instalación Paso a Paso — Jarvis App

## Requisitos previos

Antes de empezar, asegúrate de tener instalado:

- **Android Studio** Iguana (2023.2.1) o superior → https://developer.android.com/studio
- **JDK 17** (incluido con Android Studio)
- **Git** → https://git-scm.com
- **NDK r25c o superior** (ver paso 3)
- Un teléfono Android con **depuración USB activada** (API 26+, recomendado API 33+)

---

## PASO 1 — Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/JarvisApp.git
cd JarvisApp
```

---

## PASO 1.5 — Generar Gradle Wrapper (si tu plataforma no acepta binarios en PR)

Este repositorio **no versiona** `gradle/wrapper/gradle-wrapper.jar` para evitar bloqueos en plataformas que rechazan archivos binarios en Pull Requests.

Desde la raíz del proyecto ejecuta:

```bash
gradle wrapper --gradle-version 8.2 --distribution-type bin
chmod +x gradlew
```

Esto generará/recreará automáticamente:
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

> Requisito: tener `gradle` instalado globalmente solo para esta primera generación. Después podrás usar `./gradlew` normalmente.

---

## PASO 2 — Instalar el NDK en Android Studio

1. Abre **Android Studio**
2. Ve a **File → Settings → Appearance & Behavior → System Settings → Android SDK**
3. Pestaña **SDK Tools** → Activa **NDK (Side by side)**
4. Selecciona versión **25.2.9519653** o superior
5. Aplica los cambios

---

## PASO 3 — Preparar llama.cpp (OBLIGATORIO)

Desde la terminal, en la raíz del proyecto:

```bash
chmod +x scripts/setup_llama_cpp.sh
bash scripts/setup_llama_cpp.sh
```

Esto clona automáticamente:
- `llama.cpp` → en `app/src/main/cpp/llama.cpp/`
- `stable-diffusion.cpp` → en `app/src/main/cpp/stable-diffusion.cpp/`

⏱️ Tiempo estimado: 2–5 minutos según conexión.

---

## PASO 4 — Abrir en Android Studio

1. **File → Open** → Selecciona la carpeta `JarvisApp/`
2. Espera la sincronización de Gradle (la primera vez tarda 3–8 min)
3. Si hay errores de sync: **File → Invalidate Caches → Restart**

---

## PASO 5 — Compilar la app

```
Build → Make Project    (Ctrl+F9 / Cmd+F9)
```

La primera compilación incluye la compilación de llama.cpp con el NDK, puede tardar **5–15 minutos**.

---

## PASO 6 — Instalar en tu dispositivo

### Activar depuración USB en el teléfono:
1. **Ajustes → Acerca del teléfono** → Toca 7 veces en **Número de compilación**
2. **Ajustes → Opciones de desarrollador** → Activa **Depuración USB**
3. Conecta el cable USB y acepta la autorización en el teléfono

### Instalar:
```
Run → Run 'app'    (Shift+F10 / Ctrl+R)
```

Selecciona tu dispositivo en el selector y espera la instalación.

---

## PASO 7 — Descargar modelos

### Desde la app (recomendado):

1. Abre **Jarvis App**
2. Toca el ícono **🤖 Modelos** en la barra inferior
3. Para empezar, te recomendamos:
   - **SmolLM2 360M** (400 MB) si tienes poca RAM
   - **Phi-3 Mini Q4** (2.4 GB) para el mejor balance calidad/velocidad
4. Toca **Descargar** → espera en background
5. Toca **Activar** cuando termine
6. Ve a **Chat** y empieza a conversar

### Via script ADB (para múltiples modelos a la vez):

```bash
# Con el teléfono conectado:
bash scripts/download_models.sh
```

Sigue el menú interactivo.

---

## PASO 8 — (Opcional) Stable Diffusion y Moondream

Para generación de imágenes y análisis visual:

1. Ve a **Modelos → 🎨 Imagen** → Descarga **SD Turbo**
2. Ve a **Modelos → 👁️ Visión** → Descarga **Moondream 2**
3. Activa cada uno
4. Accede desde el menú chat o la pantalla dedicada

---

## Solución de problemas frecuentes

### ❌ "cmake not found" durante la build
→ Android Studio → SDK Manager → SDK Tools → CMake → Instalar versión 3.22+

### ❌ "NDK not configured" 
→ Agrega en `local.properties`:
```
ndk.dir=/ruta/a/tu/ndk
```

### ❌ App crashea al abrir con "UnsatisfiedLinkError"
→ El .so nativo no compiló. Vuelve a ejecutar `setup_llama_cpp.sh` y recompila.

### ❌ "No se pudo cargar el modelo" en el chat
→ Verifica que el .gguf esté en `/sdcard/JarvisApp/models/llm/` y que el modelo esté marcado como **Activo** en la pantalla de Modelos.

### ❌ Generación muy lenta (< 3 tokens/segundo)
→ Usa un modelo más pequeño como **SmolLM2 360M** o **Qwen 1.5B**. Cierra otras apps para liberar RAM.

### ❌ Error de permisos de almacenamiento
→ Ajustes del sistema → Apps → Jarvis → Permisos → Almacenamiento → Permitir siempre.

---

## ✅ Verificación final

Si todo está bien, al abrir la app deberías ver:
- Pantalla de chat con el ícono ⚡ Jarvis
- Panel inferior con: Chat · Panel · Modelos · Archivos
- Al ir a **Panel**, las barras de RAM/CPU se actualizan en tiempo real
- Al ir a **Modelos**, la lista de modelos disponibles para descargar

¡Listo! Ya tienes tu asistente Jarvis con IA completamente local. 🎉
