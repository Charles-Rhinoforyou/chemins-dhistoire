plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cheminsdhistoire.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cheminsdhistoire.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")

    // Localisation GPS
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Réseau (Wikipedia) — OkHttp + org.json natif Android (pas de génération de code)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Images d'illustration
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // IA générative locale sur le téléphone (OPTIONNELLE).
    // Le narrateur IA (LlmNarrator) fonctionne par réflexion : l'appli compile et tourne
    // sans cette ligne (repli automatique sur le narrateur local). Pour activer Gemma
    // sur l'appareil, décommentez la ligne ci-dessous (vérifiez la dernière version dispo)
    // puis déposez un modèle .task dans /files/models/ :
    // implementation("com.google.mediapipe:tasks-genai:0.10.14")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
