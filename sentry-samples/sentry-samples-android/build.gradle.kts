plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdkVersion(Config.Android.compileSdkVersion)

    defaultConfig {
        applicationId = "io.sentry.samples.android"
        minSdkVersion(Config.Android.minSdkVersionNdk)
        targetSdkVersion(Config.Android.targetSdkVersion)
        versionCode = 2
        versionName = "1.1.0"

        externalNativeBuild {
            val sentryNativeSrc = if (File("${project.projectDir}/../../sentry-android-ndk/sentry-native-local").exists()) {
                "sentry-native-local"
            } else {
                "sentry-native"
            }
            println("sentry-samples-android: $sentryNativeSrc")

            cmake {
                arguments.add(0, "-DANDROID_STL=c++_static")
                arguments.add(0, "-DSENTRY_NATIVE_SRC=$sentryNativeSrc")
            }
        }

        ndk {
            abiFilters.addAll(Config.Android.abiFilters)
        }
    }

    ndkVersion = Config.Android.ndkVersion

    buildFeatures {
        // Determines whether to support View Binding.
        // Note that the viewBinding.enabled property is now deprecated.
        viewBinding = true
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            addManifestPlaceholders(mapOf(
                "sentryDebug" to true,
                "sentryEnvironment" to "debug"
            ))
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
            isShrinkResources = true

            addManifestPlaceholders(mapOf(
                "sentryDebug" to false,
                "sentryEnvironment" to "release"
            ))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":sentry-android"))

//    how to exclude androidx if release health feature is disabled
//    implementation(project(":sentry-android")) {
//        exclude(group = "androidx.lifecycle", module = "lifecycle-process")
//        exclude(group = "androidx.lifecycle", module = "lifecycle-common-java8")
//    }

    implementation(Config.Libs.appCompat)

    debugImplementation(Config.Libs.leakCanary)
}
