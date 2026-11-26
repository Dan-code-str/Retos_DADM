# Reto 12 — Delegando la autenticación

**Autor:** Daniel Santiago Ramírez Rocha · 2179689508
Desarrollo de Aplicaciones para Dispositivos Móviles — Universidad Nacional de Colombia

App Android (Kotlin + vistas XML con Material 3) que delega la autenticación en **GitHub** a través de **Firebase Authentication**.

| Requisito del reto | Cómo se cumple |
|---|---|
| Proveedor externo (red social, GitHub, Firebase…) | GitHub como proveedor OAuth, gestionado con Firebase Authentication |
| La app no almacena contraseñas | No hay ningún campo de contraseña en la app. Solo se conserva la sesión (token) que emite Firebase |
| Redirigir al servicio del tercero, que pide las credenciales | `startActivityForSignInWithProvider` abre la página oficial de GitHub en una Custom Tab del navegador; allá el usuario escribe su usuario, contraseña y 2FA |
| Mensaje de éxito o de fallo | Pantalla «¡Autenticación exitosa!» con los datos del perfil, o «Autenticación fallida» con el motivo (cancelado, sin internet, proveedor deshabilitado, etc.), más un Toast en ambos casos |

## Archivos

```
app/src/main/java/co/edu/unal/reto12/
├── MainActivity.kt        Pantalla única (vistas XML + ViewBinding); muestra inicio, éxito o fallo y los Toast
├── AuthViewModel.kt       Estado de la pantalla: sin sesión / redirigiendo / exitoso / fallido
└── AutenticadorGithub.kt  Lógica de Firebase Auth y traducción de errores a mensajes legibles
app/src/main/res/layout/activity_main.xml   Las tres vistas (inicio, éxito, fallo)
```

El proyecto usa la misma base de Gradle que reto11 (AGP 9.3.3, Gradle 9.5.0, minSdk 24), más Firebase BoM 34.17.0 y el plugin `com.google.gms.google-services` 4.6.0.
No se usa la BoM 35.x porque trae Firebase Auth 25, compilado con Kotlin 2.4, y el Kotlin que incluye AGP 9.3.3 todavía no lo puede leer (*incompatible version of Kotlin… metadata 2.4.0*).
El plugin solo se aplica cuando existe `app/google-services.json`. Sin ese archivo el proyecto igual compila, y la app avisa en pantalla que falta configurar Firebase.

---

## Configuración de Firebase y GitHub (una sola vez)

### 1. Abrir el proyecto

En Android Studio: **File → Open → `Retos_Android_DADM/reto12`**, y espera a que termine el *Gradle Sync*.

### 2. Registrar la app en Firebase

1. Entra a <https://console.firebase.google.com>, crea un proyecto (por ejemplo `reto12-dadm`) o reutiliza uno. Analytics no hace falta.
2. **Agregar app → Android**, con el paquete `co.edu.unal.reto12`.
3. Agrega la **huella SHA-1** de tu llave de depuración. En una terminal de Windows:
   ```
   keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   ```
   Copia la línea `SHA1: ...` y pégala en Firebase (también se puede agregar después en *Configuración del proyecto → Tus apps*).
4. Descarga **`google-services.json`** y ponlo en la carpeta **`app/`** del proyecto.

### 3. Crear la OAuth App en GitHub

1. En Firebase: **Authentication → Comenzar → Método de inicio de sesión → GitHub**. Copia la **URL de devolución de llamada** que muestra, que tiene la forma `https://<id-proyecto>.firebaseapp.com/__/auth/handler`. Deja esa ventana abierta.
2. En GitHub: **Settings → Developer settings → OAuth Apps → New OAuth App**:
   - Application name: `Reto 12 DADM`
   - Homepage URL: `https://<id-proyecto>.firebaseapp.com`
   - Authorization callback URL: la URL que copiaste en el paso anterior
3. **Register application**. Copia el **Client ID** y genera un **Client secret** con *Generate a new client secret*.

### 4. Habilitar GitHub en Firebase

De vuelta en la ventana de Firebase del paso 3.1: activa **Habilitar**, pega el *Client ID* y el *Client secret* y dale **Guardar**.

> El *client secret* solo vive en Firebase, no en el código de la app.

### 5. Probar

Después de copiar `google-services.json`, haz **File → Sync Project with Gradle Files** para que se aplique el plugin.


Ejecuta la app en un emulador con Google Play o en un celular:

- **Éxito:** toca *Continuar con GitHub*, inicia sesión en GitHub y autoriza la app. Debes ver «¡Autenticación exitosa!» con tu nombre, usuario y correo.
- **Fallo:** toca el botón y cierra la página de GitHub sin iniciar sesión (aparece «Autenticación fallida — cerraste la página…»), o prueba en modo avión (aparece «Sin conexión»).
- **Sesión persistente:** cierra y vuelve a abrir la app. Debe mostrar «Sesión activa» sin pedir credenciales otra vez. *Cerrar sesión* la termina.

> Para que GitHub vuelva a pedir la contraseña en una demo, cierra también la sesión de github.com en el navegador del dispositivo. Si no, GitHub te reconoce y solo pide autorizar.

## Problemas comunes

| Mensaje | Causa probable |
|---|---|
| `ERROR_OPERATION_NOT_ALLOWED` | El proveedor GitHub no está habilitado en Firebase (paso 4) |
| GitHub muestra *«The redirect_uri is not associated with this application»* | La *Authorization callback URL* de GitHub no coincide exactamente con la de Firebase |
| `ERROR_WEB_INTERNAL_ERROR` / `invalid client` | El Client ID o el Client secret están mal copiados en Firebase |
| La app dice «Firebase aún no está configurado» | `google-services.json` no está en `app/`, o no se hizo *Sync* después de copiarlo |
| `No matching client found for package name` | El JSON se descargó para otro paquete: debe ser `co.edu.unal.reto12` |
| Ya existe una cuenta con ese correo | Ese correo ya se registró en el proyecto de Firebase con otro proveedor |
