import org.gradle.kotlin.dsl.implementation

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.jetbrains.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.language.repeater"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.language.repeater"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }

  buildFeatures {
    viewBinding = true
  }

  packaging {
    jniLibs {
      // 遇到 libc++_shared.so 冲突时，优先选取第一个
      pickFirst("lib/**/libc++_shared.so")
    }
  }
}

dependencies {
  implementation(libs.androidx.navigation.fragment)
  implementation(libs.androidx.navigation.ui)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.activity)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.documentfile)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)

  implementation(libs.ffmpeg.kit.x6kb)
  implementation(libs.androidx.exoplayer)
  implementation(libs.androidx.exoplayer.ui)
  implementation(libs.androidx.exoplayer.session)
  implementation(libs.vad.webrtc)
  implementation(libs.vad.silero)

  implementation(libs.kotlinx.serialization.json)
  implementation(libs.lottie)
  implementation(libs.google.flexbox)
  implementation(libs.coil)
  implementation(libs.coil.video)
  implementation(libs.datastore.preference)

  //room
  ksp(libs.androidx.room.compiler)
  // 2. Room 核心库
  implementation(libs.androidx.room.runtime)
  // 3. Room KTX (支持协程和 Flow)
  implementation(libs.androidx.room.ktx)
}