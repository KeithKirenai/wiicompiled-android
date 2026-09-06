import java.io.IOException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun toolOnPath(tool: String): Boolean {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val lookup = if (isWindows) "where" else "which"
    return try {
        val process = ProcessBuilder(lookup, tool).redirectErrorStream(true).start()
        process.waitFor() == 0
    } catch (e: IOException) {
        false
    }
}

val ccacheArgs = if (toolOnPath("ccache")) {
    listOf("-DCMAKE_C_COMPILER_LAUNCHER=ccache", "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache")
} else {
    emptyList()
}

android {
    namespace = "com.wiicompiled.mkw"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.wiicompiled.mkw"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++20", "-fexceptions", "-frtti", "-O3", "-DNDEBUG")
                arguments(
                    *(
                        listOf("-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release") + ccacheArgs
                    ).toTypedArray()
                )
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("**/libmkw_base_shared.a")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
}
