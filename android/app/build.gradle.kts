plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingStore = System.getenv("ANDROID_KEYSTORE_PATH")
val signingStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val signingAlias = System.getenv("ANDROID_KEY_ALIAS")
val signingKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

android {
    namespace = "ru.viceburg.offline"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.viceburg.offline"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (signingStore != null && signingStorePassword != null && signingAlias != null && signingKeyPassword != null) {
                storeFile = file(signingStore)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

val repoRoot = rootProject.projectDir.parentFile
tasks.register<Exec>("buildGameHtml") {
    workingDir = repoRoot
    val python = if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3"
    commandLine(python, "tools/build_game.py")
    inputs.files(
        repoRoot.resolve("upstream/original/viceburg_mod_clean.html"),
        repoRoot.resolve("localization/catalog/ru.json"),
        repoRoot.resolve("tools/build_game.py")
    )
    outputs.file(repoRoot.resolve("android/app/src/main/assets/game/index.html"))
}
tasks.named("preBuild").configure { dependsOn("buildGameHtml") }

dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
}
