import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.detekt)
}

val envProperties = Properties().apply {
    val file = rootProject.file(".env")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun optionalProperty(key: String): String {
    val value = envProperties.getProperty(key) ?: System.getenv(key) ?: ""
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}
// Release-feed configuration for the in-app updater. Both are optional and compile-time only:
//   UPDATE_FEED_URL   — a backend/static URL serving the release JSON (GitHub-shaped or normalised).
//                       When empty, the updater reads the public releases repo unauthenticated; no
//                       GitHub token may ship inside the APK (see docs/UPDATE_SYSTEM.md §1/§15).
//   UPDATE_REPO_SLUG  — owner/repo override; defaults to Aniket-Man/FOCUSSHEILD-releases.
val updateFeedUrl = optionalProperty("UPDATE_FEED_URL")
val updateRepoSlug = optionalProperty("UPDATE_REPO_SLUG")
val updateFirebaseProjectId = optionalProperty("UPDATE_FIREBASE_PROJECT_ID")
val updateFirebaseApiKey = optionalProperty("UPDATE_FIREBASE_API_KEY")
val updateFirebaseStorageBucket = optionalProperty("UPDATE_FIREBASE_STORAGE_BUCKET")
val updateFirebaseAppId = optionalProperty("UPDATE_FIREBASE_APP_ID")
val updateFirebaseDatabaseId = optionalProperty("UPDATE_FIREBASE_DATABASE_ID")
val updateChannelToken = optionalProperty("UPDATE_CHANNEL_TOKEN")
val updateConnectionToken = optionalProperty("UPDATE_CONNECTION_TOKEN")

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.focusshield.krtjfp"
    minSdk = 24
    targetSdk = 35
    multiDexEnabled = true
    // Bumped by hand per release — never per build.
    versionCode = 5
    versionName = "1.4.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "UPDATE_FEED_URL", "\"$updateFeedUrl\"")
    buildConfigField("String", "UPDATE_REPO_SLUG", "\"$updateRepoSlug\"")
    buildConfigField("String", "UPDATE_FIREBASE_PROJECT_ID", "\"$updateFirebaseProjectId\"")
    buildConfigField("String", "UPDATE_FIREBASE_API_KEY", "\"$updateFirebaseApiKey\"")
    buildConfigField("String", "UPDATE_FIREBASE_STORAGE_BUCKET", "\"$updateFirebaseStorageBucket\"")
    buildConfigField("String", "UPDATE_FIREBASE_APP_ID", "\"$updateFirebaseAppId\"")
    buildConfigField("String", "UPDATE_FIREBASE_DATABASE_ID", "\"$updateFirebaseDatabaseId\"")
    buildConfigField("String", "UPDATE_CHANNEL_TOKEN", "\"$updateChannelToken\"")
    buildConfigField("String", "UPDATE_CONNECTION_TOKEN", "\"$updateConnectionToken\"")
  }

  signingConfigs {
    // Release signing is only wired in when the keystore file and both credentials exist.
    // A fresh clone without my-upload-key.jks / STORE_PASSWORD / KEY_PASSWORD still builds;
    // signed releases require providing them (issue #24).
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystorePassword = System.getenv("STORE_PASSWORD")
      val signingKeyPassword = System.getenv("KEY_PASSWORD")
      if (keystorePath.isNotEmpty() &&
        file(keystorePath).exists() &&
        !keystorePassword.isNullOrEmpty() &&
        !signingKeyPassword.isNullOrEmpty()
      ) {
        storeFile = file(keystorePath)
        storePassword = keystorePassword
        keyAlias = "upload"
        keyPassword = signingKeyPassword
        enableV1Signing = true
        enableV2Signing = true
      }
    }
    // Shared debug keystore — only used when the file is actually present in the repo root.
    create("debugConfig") {
      val sharedDebugKeystore = rootProject.file("debug.keystore")
      if (sharedDebugKeystore.exists()) {
        storeFile = sharedDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Attach signing only when the config was fully populated (keystore + passwords present);
      // otherwise the release build proceeds unsigned instead of failing on a fresh clone.
      val releaseSigning = signingConfigs.getByName("release")
      if (releaseSigning.storeFile?.exists() == true && releaseSigning.storePassword != null) {
        signingConfig = releaseSigning
      }
    }
    debug {
      // Fall back to AGP's default debug keystore when the repo's shared one is absent,
      // so `assembleDebug` works out of the box on any fresh clone (issue #24).
      val sharedDebug = signingConfigs.getByName("debugConfig")
      if (sharedDebug.storeFile?.exists() == true) {
        signingConfig = sharedDebug
      }
    }
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
  ignoreList.add("UPDATE_FEED_URL")
  ignoreList.add("UPDATE_REPO_SLUG")
  ignoreList.add("UPDATE_FIREBASE_PROJECT_ID")
  ignoreList.add("UPDATE_FIREBASE_API_KEY")
  ignoreList.add("UPDATE_FIREBASE_STORAGE_BUCKET")
  ignoreList.add("UPDATE_FIREBASE_APP_ID")
  ignoreList.add("UPDATE_FIREBASE_DATABASE_ID")
  ignoreList.add("UPDATE_CHANNEL_TOKEN")
  ignoreList.add("UPDATE_CONNECTION_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

detekt {
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

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
  // ProcessLifecycleOwner: drives the cloud auto-save flush when the last activity leaves the
  // foreground. Declared explicitly rather than inherited transitively.
  implementation(libs.androidx.lifecycle.process)
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
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.androidx.security.crypto)
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
  implementation(libs.hilt.android)
  "ksp"(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.hilt.work)
  "ksp"(libs.hilt.work.compiler)
}

tasks.withType<Test> { failFast = true }

// Room schema export (FocusShieldDatabase.exportSchema = true): KSP writes the versioned JSON
// schemas here so future migrations can be validated against them instead of relying on
// destructive fallbacks. The first build on this config generates app/schemas/ — commit it.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// NOTE on screenshot goldens.
//
// Goldens are written into the source tree (`app/src/test/screenshots`) by the explicit
// `filePath` in GreetingScreenshotTest, so a drift shows up in a code-review diff. Roborazzi's
// own `verifyRoborazziDebug` task is deliberately NOT wired into `check`: it does not manage
// images captured with an explicit `filePath` and throws a NullPointerException inside
// `captureRoboImage` when it tries to. Use the flag form instead, which does work:
//
//     ./gradlew testDebugUnitTest -Proborazzi.test.record=true   # re-record
//     ./gradlew testDebugUnitTest -Proborazzi.test.verify=true   # compare
//
// The test itself asserts that the screen actually rendered and that an image was really written,
// so it can no longer pass vacuously the way it used to (issue #31).
