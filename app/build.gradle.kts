plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("com.google.devtools.ksp")
}

val suryaVersionCode = providers.environmentVariable("SURYA_VERSION_CODE")
 .orNull?.toIntOrNull() ?: 2
val suryaKeystoreFile = providers.environmentVariable("SURYA_KEYSTORE_FILE").orNull
val suryaKeystorePassword = providers.environmentVariable("SURYA_KEYSTORE_PASSWORD").orNull
val suryaKeyAlias = providers.environmentVariable("SURYA_KEY_ALIAS").orNull
val suryaKeyPassword = providers.environmentVariable("SURYA_KEY_PASSWORD").orNull
val hasSuryaSigning = listOf(suryaKeystoreFile, suryaKeystorePassword, suryaKeyAlias, suryaKeyPassword)
 .all { !it.isNullOrBlank() }

android {
 namespace = "in.surya.optician"
 compileSdk = 35
 defaultConfig {
  applicationId = "in.surya.optician"
  minSdk = 26
  targetSdk = 35
  versionCode = suryaVersionCode
  versionName = "1.0.$suryaVersionCode"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 signingConfigs {
  create("suryaStable") {
   if (hasSuryaSigning) {
    storeFile = file(suryaKeystoreFile!!)
    storePassword = suryaKeystorePassword
    keyAlias = suryaKeyAlias
    keyPassword = suryaKeyPassword
   }
  }
 }
 buildTypes {
  getByName("debug") {
   // CI uses one persistent signing key so every distributed APK can update the previous one.
   // Local developer builds retain Android's normal local debug signing when secrets are absent.
   if (hasSuryaSigning) signingConfig = signingConfigs.getByName("suryaStable")
  }
 }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
 val bom = platform("androidx.compose:compose-bom:2024.12.01")
 implementation(bom)
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 ksp("androidx.room:room-compiler:2.6.1")
 testImplementation("junit:junit:4.13.2")
}
