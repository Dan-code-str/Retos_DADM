# Reto 11 · Lienzo IA

App Android (Kotlin, vistas XML) que genera imágenes con IA generativa a partir de la interacción con el usuario:

- **Imágenes:** Cloudflare Workers AI con FLUX.1 [schnell] (plan gratuito), o el modelo de imagen de Gemini si el proyecto tiene facturación.
- **Texto:** Google Gemini mejora la idea del usuario y traduce el prompt al inglés para FLUX.

## Qué hace

1. El usuario escribe una idea libre.
2. Elige un **estilo**, un **ambiente** (opcional) y un **formato** (relación de aspecto) con chips.
3. La app arma el prompt final (`ConstructorPrompt`), Gemini lo traduce al inglés y se envía a FLUX.
4. Muestra la imagen junto con el prompt enviado.
5. **Refinar**: el usuario escribe un cambio ("que sea de noche"). Con FLUX se regenera con el prompt original + el cambio (en Cloudflare, FLUX schnell no admite semilla ni edición de imágenes); con Gemini se envía la imagen para que la edite.
6. **Mejorar con IA**: un modelo de texto de Gemini reescribe la idea como un prompt más detallado.
7. Guardar en la galería (Android 10+), compartir y ver un historial de las últimas 20 creaciones.

## Configuración

1. Crea una API key de Gemini en <https://aistudio.google.com/apikey>.
2. Crea una cuenta gratis de Cloudflare; copia tu **Account ID** y crea un **API Token** con permiso *Workers AI*.
3. Agrega todo a `reto11/local.properties` (ese archivo no se sube a git):

   ```properties
   GEMINI_API_KEY=tu_clave
   CLOUDFLARE_ACCOUNT_ID=tu_account_id
   CLOUDFLARE_API_TOKEN=tu_token
   # Opcionales:
   # CLOUDFLARE_MODELO=@cf/black-forest-labs/flux-1-schnell
   # GEMINI_MODELO_IMAGEN=gemini-2.5-flash-image
   # GEMINI_MODELO_TEXTO=gemini-flash-latest
   ```

   Sin credenciales de Cloudflare, la app usa el modelo de imagen de Gemini.

   También puedes pegar la clave dentro de la app con el ícono de la llave.
4. Sincroniza Gradle y ejecuta.

> **Por qué Cloudflare:** los modelos de imagen de Gemini no tienen cuota gratuita (responden 429) y la facturación de Google no se pudo activar. Cloudflare da 10.000 *neurons* diarios gratis (~130 imágenes con 6 pasos), que se renuevan a las 00:00 UTC.

## Estructura

Los archivos Kotlin siguen la convención de Kotlin de omitir el paquete raíz común (`co.edu.unal.reto11`) en las carpetas:

| Archivo | Responsabilidad |
|---|---|
| `MainActivity.kt` | UI: chips, estados de carga/error, resultado, historial, diálogo de API key |
| `GeneradorViewModel.kt` | Estado de la pantalla (`StateFlow`), coordinación de llamadas, mensajes de error |
| `data/GeneradorImagenes.kt` | Interfaz común para cualquier servicio de imágenes |
| `data/CloudflareCliente.kt` | FLUX en Cloudflare Workers AI (`/accounts/{id}/ai/run/{modelo}`) |
| `data/GeminiCliente.kt` | Llamadas REST a `generativelanguage.googleapis.com`: mejorar idea, traducir, imagen |
| `data/ConstructorPrompt.kt` | Opciones de estilo/ambiente/formato y armado del prompt |
| `data/Almacen.kt` | Guarda las imágenes en el almacenamiento interno, MediaStore y FileProvider |

### Petición a Cloudflare

```json
POST https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/ai/run/@cf/black-forest-labs/flux-1-schnell
Authorization: Bearer …
{ "prompt": "A glass hummingbird flying over…", "steps": 6, "seed": 12345 }
→ { "result": { "image": "<jpeg base64>" }, "success": true }
```

### Petición a Gemini (modelo de imagen)

```json
POST /v1beta/models/gemini-2.5-flash-image:generateContent
x-goog-api-key: …
{
  "contents": [{ "role": "user", "parts": [ {"inlineData": …imagen previa si se refina…}, {"text": "Genera una imagen de: …"} ] }],
  "generationConfig": { "responseModalities": ["TEXT", "IMAGE"], "imageConfig": { "aspectRatio": "16:9" } }
}
```

La imagen llega en `candidates[0].content.parts[].inlineData.data` (base64).

Autor: Daniel Santiago Ramírez Rocha · 2179689508
