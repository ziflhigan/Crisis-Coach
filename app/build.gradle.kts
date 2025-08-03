plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.objectbox)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.cautious5.crisis_coach"
    compileSdk = 35

    androidResources {
        noCompress.add("tflite")
    }

    packaging {
        resources {
            // Exclude all files that might cause conflicts from the META-INF directory
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    defaultConfig {
        applicationId = "com.cautious5.crisis_coach"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.viewfinder)

    implementation(libs.litert)
    implementation(libs.litert.support.api)
    implementation(libs.litert.gpu.api)

    implementation(libs.objectbox.android)
    implementation(libs.androidx.exifinterface)
    kapt(libs.objectbox.processor)

    implementation(libs.androidx.material.icons.extended)
    implementation(libs.icons.lucide.android)
    implementation(libs.fontawesomecompose)

    implementation(libs.androidx.core.splashscreen)

    implementation(libs.tasks.text)
    implementation(libs.tasks.genai)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.accompanist.permissions)
    implementation(libs.gson)

    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)

    implementation(libs.itext7.core.android)
    implementation(libs.slf4j.android)

    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
}