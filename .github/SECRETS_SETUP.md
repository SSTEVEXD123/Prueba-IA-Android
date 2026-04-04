# 🔐 Configuración de Secrets para GitHub Actions

Para que el workflow de **Release firmado** funcione,
necesitas añadir los siguientes secrets en tu repositorio.

---

## Dónde añadir los secrets

**GitHub → Tu repo → Settings → Secrets and variables → Actions → New repository secret**

---

## Secrets obligatorios para Release firmado

| Secret | Descripción |
|---|---|
| `KEYSTORE_BASE64` | Keystore Android codificado en Base64 |
| `KEYSTORE_PASSWORD` | Contraseña del keystore |
| `KEY_ALIAS` | Alias de la clave dentro del keystore |
| `KEY_PASSWORD` | Contraseña de la clave |

---

## Paso a paso: generar el keystore

### 1. Crear el keystore (si no tienes uno)

```bash
keytool -genkey -v \
  -keystore jarvis_release.keystore \
  -alias jarvis_key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass TU_STORE_PASSWORD \
  -keypass TU_KEY_PASSWORD \
  -dname "CN=Jarvis App, OU=Mobile, O=TuEmpresa, L=Ciudad, S=Estado, C=ES"
```

### 2. Codificar el keystore a Base64

```bash
# macOS / Linux
base64 -i jarvis_release.keystore | tr -d '\n' > keystore_base64.txt
cat keystore_base64.txt

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("jarvis_release.keystore")) | Out-File keystore_base64.txt
```

### 3. Añadir el contenido de `keystore_base64.txt` al secret `KEYSTORE_BASE64`

⚠️ Guarda el archivo `jarvis_release.keystore` y sus contraseñas en un lugar seguro.
**¡Nunca subas el keystore ni las contraseñas al repositorio!**

---

## Si NO configuras los secrets

La pipeline funciona igualmente pero:
- Los APKs de Release **no estarán firmados**
- No podrás subir la app a Google Play
- Puedes instalar el APK manualmente (debug o release sin firma)

---

## Verificar que los secrets funcionan

Una vez añadidos, haz push a `main` o crea un tag `v1.0.0`:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Esto disparará el workflow `github-release` que:
1. Compila el APK firmado
2. Crea un GitHub Release con el APK adjunto
3. Genera el changelog automático
