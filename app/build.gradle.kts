import java.util.Properties
import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.serialization)
  alias(libs.plugins.jetbrains.kotlin.compose)
  alias(libs.plugins.ksp)
//  alias(libs.plugins.google.gms.google.services)
//  alias(libs.plugins.firebase.crashlytics)
}

// Firebase 构建插件（google-services / crashlytics）只在 google-services.json 存在时启用：
// 该插件负责把 json 转成运行时配置、并在 release 后上传混淆映射。
// 缺文件时强启会让所有构建在 processDebugGoogleServices 直接失败，因此按文件存在与否动态挂载。
// json 与插件默认搜索位置一致，放在 app/google-services.json。
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
  apply(plugin = "com.google.gms.google-services")
  apply(plugin = "com.google.firebase.crashlytics")
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_11
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.incremental", "true")
}

// release 签名信息从项目根目录的 keystore.properties 读取（该文件不入库），
// 缺失时 release 不签名，本地开发仍可正常构建调试
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
  keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
  namespace = "com.language.repeater"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.language.repeater"
    minSdk = 24
    versionCode = 1
    versionName = "0.10"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // keystore.properties 存在时才注册 release 签名，避免缺失密钥文件时构建失败
    if (keystorePropertiesFile.exists()) {
      create("release") {
        storeFile = file(keystoreProperties.getProperty("storeFile"))
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      // keystore.properties 存在时才用 release 签名，生成可上架 Play 的签名 AAB
      if (keystorePropertiesFile.exists()) {
        signingConfig = signingConfigs.getByName("release")
      }
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    viewBinding = true
    compose = true
    buildConfig = true
  }

  packaging {
    jniLibs {
      // 遇到 libc++_shared.so 冲突时，优先选取第一个
      pickFirsts.add("lib/**/libc++_shared.so")
    }
    resources {
      // Android 15+ 侧载安装时会预编译库合并进来的 baseline profile，失败即 INSTALL_BASELINE_PROFILE_FAILED；
      // 本应用没有自产 profile，去掉只影响启动优化、不影响功能与 Play 上架
      excludes += "assets/dexopt/baseline.prof"
      excludes += "assets/dexopt/baseline.profm"
    }
  }

  // 仅当配置了 Firebase 时引入独立资源目录：目录里的 keep.xml 保住 google_app_id 等字符串，
  // 否则 release 资源收缩会把仅靠名字引用（getIdentifier）的 Firebase 配置裁掉
  if (googleServicesFile.exists()) {
    sourceSets.getByName("main").res.srcDirs("src/firebaseRes")
  }
}

dependencies {
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.androidx.fragment.ktx)
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
  //implementation(libs.vad.webrtc)
  //implementation(libs.vad.silero)

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

  implementation(libs.androidx.palette)

  //compose
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.activity.compose)
  implementation(libs.compose.viewmodel)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.ui.tooling.preview)

  implementation(libs.icons.tabler.outline.android)
  implementation(libs.icons.tabler.filled.android)

  //firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)

  implementation(libs.androidx.media3.ui.compose)
  implementation(libs.androidx.media3.ui.compose.material3)

  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
}