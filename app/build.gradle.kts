import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val envProperties = Properties().apply {
    val file = rootProject.file(".env")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun optionalProperty(key: String): String {
    val value = envProperties.getProperty(key) ?: System.getenv(key) ?: ""
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}
val supabaseUrl = optionalProperty("SUPABASE_URL")
val supabaseAnonKey = optionalProperty("SUPABASE_ANON_KEY")
// Release-feed configuration for the in-app updater. Both are optional and compile-time only:
//   UPDATE_FEED_URL   — a backend/static URL serving the release JSON (GitHub-shaped or normalised).
//                       This is how the updater works while the GitHub repository is private, since
//                       no GitHub token may ship inside the APK (see docs/UPDATE_SYSTEM.md §1/§15).
//   UPDATE_REPO_SLUG  — owner/repo override for forks that publish their own releases.
val updateFeedUrl = optionalProperty("UPDATE_FEED_URL")
val updateRepoSlug = optionalProperty("UPDATE_REPO_SLUG")

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.focusshield.krtjfp"
    minSdk = 24
    targetSdk = 35
    // Bumped by hand per release — never per build.
    versionCode = 4
    versionName = "1.3.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    buildConfigField("String", "UPDATE_FEED_URL", "\"$updateFeedUrl\"")
    buildConfigField("String", "UPDATE_REPO_SLUG", "\"$updateRepoSlug\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
      enableV1Signing = true
      enableV2Signing = true
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("SUPABASE_URL")
  ignoreList.add("SUPABASE_ANON_KEY")
  ignoreList.add("UPDATE_FEED_URL")
  ignoreList.add("UPDATE_REPO_SLUG")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.vico.compose)
  implementation(libs.vico.compose.m3)
  implementation(libs.vico.core)
  implementation(libs.coil.compose)
  implementation(libs.lottie.compose)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.androidx.work.runtime.ktx)
  // work-runtime's POM asks for androidx.startup:startup-runtime:1.0.0, whose AAR is not in this
  // machine's offline Gradle cache (only its .pom/.module metadata is), so resolution tries to
  // fetch it and the build dies with "No cached version available for offline mode". 1.1.1 is
  // cached *and* is what the graph already prefers via profileinstaller/emoji2 — it is the version
  // the app has been shipping with all along — so pinning it only stops Gradle reaching for the
  // absent artifact. It does not change the runtime bytecode in the APK.
  constraints {
    implementation("androidx.startup:startup-runtime:1.1.1") {
      because("1.0.0's AAR is absent from the offline cache; 1.1.1 is cached and already preferred.")
    }
  }
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
