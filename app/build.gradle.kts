plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.edgeviewer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.edgeviewer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86"))
        }
        externalNativeBuild {
            cmake {
                arguments.add("-DANDROID_STL=c++_shared")
            }
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("org.opencv:opencv:4.9.0")
    implementation("androidx.camera:camera-core:1.3.3")
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
//    implementation(project(":sdk"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}