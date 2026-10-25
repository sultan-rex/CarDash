import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Open Headunit (AGPL-3.0, https://github.com/andreknieriem/open-headunit) embedded in CarDash as
// a library. The upstream application module was converted: no applicationId, flavors or signing.
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    namespace = "com.andrerinas.openheadunit"

    buildFeatures {
        buildConfig = true
        aidl = true // needed for shizuku
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    val copyRootAssets = tasks.register<Copy>("copyRootAssets") {
        from("${project.rootDir}/CHANGELOG.md", "${project.rootDir}/LICENSE")
        into("${project.layout.buildDirectory.get().asFile}/generated/assets/root")
    }

    // Scan available locales at configuration time and store as BuildConfig field
    val resDir = file("src/main/res")
    val availableLocales = resDir.listFiles { file ->
        file.isDirectory && file.name.startsWith("values-") &&
        !file.name.contains("night") &&
        !file.name.contains("land") &&
        !file.name.contains("port") &&
        !file.name.matches(Regex("values-[whsml]\\d+.*")) &&
        !file.name.matches(Regex("values-v\\d+")) &&
        file.resolve("strings.xml").exists()
    }?.map { dir ->
        dir.name.removePrefix("values-")
    }?.sorted() ?: emptyList()

    sourceSets {
        getByName("main") {
            // The upstream "github" flavor sources (VpnControl, DummyVpnService, extra strings).
            java.srcDir("src/github/java")
            res.srcDir("src/github/res")
            assets.srcDirs("${project.layout.buildDirectory.get().asFile}/generated/assets/root")
        }
    }

    tasks.withType<com.android.build.gradle.tasks.MergeSourceSetFolders>().configureEach {
        dependsOn(copyRootAssets)
    }

    tasks.configureEach {
        if (name.contains("lint", ignoreCase = true)) {
            dependsOn(copyRootAssets)
        }
    }

    defaultConfig {
        minSdk = 22
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true

        // Library BuildConfig has no application fields, and the code reads these.
        buildConfigField("String", "APPLICATION_ID", "\"com.sulthan.cardash\"")
        buildConfigField("String", "VERSION_NAME", "\"4.0.0-alpha\"")
        buildConfigField("int", "VERSION_CODE", "120")
        buildConfigField("String", "FLAVOR", "\"github\"")
        buildConfigField("String", "AVAILABLE_LOCALES", "\"${availableLocales.joinToString(",")}\"")
        buildConfigField("String", "GIT_SHA", "\"cardash-merged\"")

        // The CarDash tablet is 32-bit ARM; arm64 covers newer devices.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    lint {
        abortOnError = false
        disable += "PackagedPrivateKey"
        disable += "FragmentLiveDataObserve"
        disable += "FragmentBackPressedCallback"
        disable += "FragmentAddMenuProvider"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("org.conscrypt:conscrypt-android:2.6.1")

    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    implementation(project(":contract"))

    implementation("androidx.multidex:multidex:2.0.1")

    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")

    // DexMaker for runtime subclassing (Hotspot Fix)
    implementation("com.linkedin.dexmaker:dexmaker:2.28.3")

    // Glide for image/GIF loading (custom loading screen)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // ZXing for QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Shizuku for root / shell access
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}
