plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// Resolve the Android test runtime through Gradle and cache it under build/.
val robolectricSdk by configurations.creating
val robolectricCompileSdk by configurations.creating
val prepareRobolectricSdk by tasks.registering(Copy::class) {
    from(robolectricSdk, robolectricCompileSdk)
    into(layout.buildDirectory.dir("robolectric-sdk"))
}
android {
    namespace = "ru.sfu.student"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.sfu.student"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.6.1"
    }
    signingConfigs {
        getByName("debug") {
            // CI restores the existing key outside Android's default user directory.
            providers.environmentVariable("STUDENTTABLE_DEBUG_KEYSTORE").orNull?.let {
                storeFile = file(it)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            val testHome = layout.buildDirectory.dir("robolectric-home").get().asFile
            it.systemProperty("user.home", testHome.absolutePath)
            it.systemProperty("java.io.tmpdir", testHome.resolve("tmp").absolutePath)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-sdk").get().asFile.absolutePath)
            it.dependsOn(prepareRobolectricSdk)
            it.doFirst { testHome.mkdirs(); testHome.resolve("tmp").mkdirs() }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    add(robolectricCompileSdk.name, "org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
    add(robolectricSdk.name, "org.robolectric:android-all-instrumented:9-robolectric-4913185-2-i7")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

