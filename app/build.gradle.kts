import com.project.starter.easylauncher.plugin.EasyLauncherExtension
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  
  alias(libs.plugins.hilt.android)
  id("org.jmailen.kotlinter") version "5.7.0"
  id("com.google.devtools.ksp")
  id("kotlin-parcelize")
}

// easylauncher is applied here (rather than via the plugins DSL) so it shares a classloader
// with the webp-imageio reader declared in the root buildscript, letting it read the .webp
// launcher icons from the command line. Because it is not on the plugins DSL, it is
// configured through the typed extension instead of generated accessors.
apply(plugin = "com.starter.easylauncher")

configure<EasyLauncherExtension> {
  buildTypes.register("debug") {
    filters(
      chromeLike(
        mapOf(
          "label" to "DEBUG",
          "ribbonColor" to "#FF6F3F",
          "labelColor" to "#FFFFFF",
          "labelPadding" to 15,
        ),
      ),
    )
  }
  // distinguishes a sideloaded .dev build from a real install on the same device's home screen
  buildTypes.register("dev") {
    filters(
      chromeLike(
        mapOf(
          "label" to "DEV",
          "ribbonColor" to "#1565C0",
          "labelColor" to "#FFFFFF",
          "labelPadding" to 15,
        ),
      ),
    )
  }
}

kotlinter {
  reporters = arrayOf("checkstyle", "plain")
  ignoreFormatFailures = false
  ignoreLintFailures = false
}

val localProperties = Properties().apply {
  rootProject.file("local.properties").takeIf { it.exists() }?.let { file -> file.inputStream().use { load(it) } }
}

tasks.named("check") {
  dependsOn("lintKotlin")
}

// Hilt runs through javac; Moshi already runs through KSP and must not also be loaded as a
// javac annotation processor. Remove this filter once the processor classpath no longer mixes them.
tasks.withType<JavaCompile>().configureEach {
  if (name.startsWith("hiltJavaCompile")) {
    doFirst {
      val original = options.annotationProcessorPath ?: return@doFirst
      options.annotationProcessorPath = original.filter { !it.name.contains("moshi-kotlin-codegen") }
    }
  }
}

configurations.all {
  // Keep the Kotlin toolchain libraries aligned when Compose Multiplatform dependencies request
  // older transitive versions. Re-check these pins when the Compose dependency graph is upgraded.
  resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
  resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Build-time overrides for the two distributable build types, "release" and "dev": -Pminified
// toggles R8 shrink+non-debuggable vs unshrunk+debuggable+coverage, independent of which one
// you're building - "release" defaults to minified, "dev" defaults to unshrunk/debug. Example for
// a small sideload build to test on a phone: ./gradlew assembleDev -Pminified=true -PsingleAbi=true
val minifiedOverride =
  (project.findProperty("minified") as String?)?.also {
    require(it in listOf("true", "false")) { "Unknown -Pminified '$it'; expected 'true' or 'false'" }
  }?.toBoolean()

// -PsingleAbi=true restricts packaging to one native ABI (-PtargetAbi, default arm64-v8a - a
// Pixel 10 is arm64-v8a only) instead of all four. See androidComponents below.
val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
val singleAbi = (project.findProperty("singleAbi") as String?)?.toBoolean() ?: false
val targetAbi = (project.findProperty("targetAbi") as String?) ?: "arm64-v8a"
require(targetAbi in knownAbis) { "Unknown targetAbi '$targetAbi'; expected one of $knownAbis" }

android {
  namespace = "org.grakovne.lissen"
  compileSdk = 37

  // Bundle the exported Room schemas into the androidTest APK so MigrationTestHelper can load them.
  sourceSets {
    getByName("androidTest") {
      assets.srcDirs(files("$projectDir/schemas"))
    }
  }

  lint {
    disable.add("MissingTranslation")
    disable.add("MissingQuantity")
  }
  
  defaultConfig {
    applicationId = "org.grakovne.lissen"
    minSdk = 28
    targetSdk = 37
    versionCode = 11211
    versionName = "1.12.11-release"
    
    testInstrumentationRunner = "org.grakovne.lissen.HiltTestRunner"
    
    if (project.hasProperty("RELEASE_STORE_FILE")) {
      signingConfigs {
        create("release") {
          storeFile = file(project.property("RELEASE_STORE_FILE")!!)
          storePassword = project.property("RELEASE_STORE_PASSWORD") as String?
          keyAlias = project.property("RELEASE_KEY_ALIAS") as String?
          keyPassword = project.property("RELEASE_KEY_PASSWORD") as String?
          enableV1Signing = true
          enableV2Signing = true
        }
      }
    }
  }
  
  
  buildTypes {
    release {
      if (project.hasProperty("RELEASE_STORE_FILE")) {
        signingConfig = signingConfigs.getByName("release")
      }
      val minified = minifiedOverride ?: true
      isMinifyEnabled = minified
      isShrinkResources = minified
      isDebuggable = !minified
      enableUnitTestCoverage = !minified
      enableAndroidTestCoverage = !minified
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
      )
    }
    // CI/E2E only - :minifiedTest targets org.grakovne.lissen.minified by name, so this stays its
    // own always-shrunk, debug-signed package regardless of the -Pminified flag above.
    create("minified") {
      initWith(getByName("release"))
      applicationIdSuffix = ".minified"
      versionNameSuffix = " (MINIFIED TEST)"
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks.add("release")
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
    }
    // sideloadable test builds: same .dev package/signing regardless of -Pminified, so one
    // install always replaces the other.
    create("dev") {
      initWith(getByName("release"))
      applicationIdSuffix = ".dev"
      versionNameSuffix = " (DEV)"
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks.add("release")
      val minified = minifiedOverride ?: false
      isMinifyEnabled = minified
      isShrinkResources = minified
      isDebuggable = !minified
      enableUnitTestCoverage = !minified
      enableAndroidTestCoverage = !minified
    }
    // kept only as the ./gradlew testDebugUnitTest compile target - not meant to be installed;
    // use "dev" (unshrunk and debuggable by default) for that instead
    debug {}
  }
  
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }


  buildFeatures {
    buildConfig = true
    compose = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1,MIT}"
    }
  }
  testOptions {
    packaging {
      resources {
        excludes += "META-INF/LICENSE.md"
        excludes += "META-INF/LICENSE-notice.md"
      }
    }
  }
  buildToolsVersion = "37.0.0"
  
  testOptions {
    unitTests.all {
      it.useJUnitPlatform()
      it.maxParallelForks = 4
    }
  }
}

androidComponents {
  // -PsingleAbi=true only: drop every prebuilt native lib ABI except targetAbi, for "release" and
  // "dev" (never "minified" - CI/E2E doesn't use this flag). Scoped to these variants via the
  // variant API rather than the module-wide `splits` DSL, so a plain assembleDev/assembleRelease
  // keeps shipping every ABI.
  if (singleAbi) {
    listOf("release", "dev").forEach { buildType ->
      onVariants(selector().withBuildType(buildType)) { variant ->
        variant.packaging.jniLibs.excludes.addAll(
          (knownAbis - targetAbi).map { "lib/$it/**" },
        )
      }
    }
  }
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation(libs.androidx.navigation.compose)
  implementation(libs.material)
  
  implementation(libs.androidx.media3.ffmpeg.decoder)
  implementation(libs.androidx.material)
  implementation(libs.compose.shimmer.android)
  
  implementation(libs.retrofit)
  implementation(libs.logging.interceptor)
  implementation(libs.okhttp)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.car.app)
  implementation(libs.androidx.collection)
  
  implementation(libs.coil.compose)
  implementation(libs.coil.svg)
  implementation(libs.hoko.blur)
  
  implementation(libs.androidx.paging.compose)
  
  implementation(libs.androidx.compose.material.icons.extended)
  
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.hilt.android)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.datasource.okhttp)
  implementation(libs.androidx.lifecycle.service)
  implementation(libs.androidx.lifecycle.process)
  
  ksp(libs.androidx.room.compiler)
  ksp(libs.hilt.android.compiler)
  ksp(libs.moshi.kotlin.codegen)
  
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3)
  
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.datasource)
  implementation(libs.androidx.media3.database)
  
  implementation(libs.timber)
  
  implementation(libs.androidx.glance)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  
  implementation(libs.acra.core)
  implementation(libs.acra.http)
  implementation(libs.acra.toast)
  
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  
  implementation(libs.converter.moshi)
  implementation(libs.moshi)
  implementation(libs.zip4j)
  
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
  
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockwebserver)
  testRuntimeOnly(libs.junit.platform.launcher)
  
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.mockk.android)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.hilt.android.testing)
  androidTestImplementation(libs.mockwebserver)
  androidTestImplementation(libs.okhttp.tls)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.glance.appwidget.testing)
  kspAndroidTest(libs.hilt.android.compiler)
}
