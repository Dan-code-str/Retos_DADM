import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// La API key de Gemini NO va en el código: se lee de local.properties (que está en .gitignore)
// o de una variable de entorno. Ejemplo en local.properties:
//   GEMINI_API_KEY=tu_clave_de_google_ai_studio
val propiedadesLocales = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

fun propiedad(nombre: String, porDefecto: String = ""): String =
    propiedadesLocales.getProperty(nombre)?.trim()
        ?: System.getenv(nombre)?.trim()
        ?: porDefecto

android {
    namespace = "co.edu.unal.reto11"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "co.edu.unal.reto11"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"${propiedad("GEMINI_API_KEY")}\"")
        // Modelos configurables sin tocar código (se pueden cambiar en local.properties)
        buildConfigField(
            "String", "MODELO_IMAGEN",
            "\"${propiedad("GEMINI_MODELO_IMAGEN", "gemini-2.5-flash-image")}\""
        )
        buildConfigField(
            "String", "MODELO_TEXTO",
            "\"${propiedad("GEMINI_MODELO_TEXTO", "gemini-flash-lite-latest")}\""
        )
        // Si hay credenciales de Cloudflare, las imágenes se generan con Workers AI (plan gratuito)
        buildConfigField("String", "CLOUDFLARE_ACCOUNT_ID", "\"${propiedad("CLOUDFLARE_ACCOUNT_ID")}\"")
        buildConfigField("String", "CLOUDFLARE_API_TOKEN", "\"${propiedad("CLOUDFLARE_API_TOKEN")}\"")
        buildConfigField(
            "String", "CLOUDFLARE_MODELO",
            "\"${propiedad("CLOUDFLARE_MODELO", "@cf/black-forest-labs/flux-1-schnell")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
