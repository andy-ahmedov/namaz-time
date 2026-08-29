import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

val pilotSigningPropertiesFile = providers
    .gradleProperty("namaztimePilotSigningProperties")
    .orElse(providers.environmentVariable("NAMAZTIME_PILOT_SIGNING_PROPERTIES"))
    .orNull
    ?.let(::file)
    ?.canonicalFile

val pilotSigning = pilotSigningPropertiesFile?.let { propertiesFile ->
    require(propertiesFile.isFile) {
        "Pilot signing properties file does not exist: $propertiesFile"
    }
    val repositoryPath = rootDir.canonicalFile.toPath()
    require(!propertiesFile.toPath().startsWith(repositoryPath)) {
        "Pilot signing properties must be stored outside the repository"
    }

    val properties = Properties().apply {
        propertiesFile.inputStream().use(::load)
    }
    fun requiredProperty(name: String): String =
        properties.getProperty(name)?.takeIf(String::isNotBlank)
            ?: error("Pilot signing property '$name' is required")

    val configuredStoreFile = File(requiredProperty("storeFile"))
    val storeFile = if (configuredStoreFile.isAbsolute) {
        configuredStoreFile.canonicalFile
    } else {
        File(propertiesFile.parentFile, configuredStoreFile.path).canonicalFile
    }
    require(storeFile.isFile) { "Pilot signing keystore does not exist: $storeFile" }
    require(!storeFile.toPath().startsWith(repositoryPath)) {
        "Pilot signing keystore must be stored outside the repository"
    }

    mapOf(
        "storeFile" to storeFile.path,
        "storeType" to requiredProperty("storeType"),
        "storePassword" to requiredProperty("storePassword"),
        "keyAlias" to requiredProperty("keyAlias"),
        "keyPassword" to requiredProperty("keyPassword"),
    )
}

android {
    namespace = "ru.namaztime.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.namaztime.tv"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "0.4.0-pilot-local"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (pilotSigning != null) {
            create("pilot") {
                storeFile = file(pilotSigning.getValue("storeFile"))
                storeType = pilotSigning.getValue("storeType")
                storePassword = pilotSigning.getValue("storePassword")
                keyAlias = pilotSigning.getValue("keyAlias")
                keyPassword = pilotSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "PILOT_LOCAL_RUNTIME", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "PILOT_LOCAL_RUNTIME", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("pilot") {
            initWith(getByName("release"))
            buildConfigField("boolean", "PILOT_LOCAL_RUNTIME", "true")
            signingConfig = signingConfigs.findByName("pilot")
        }
    }

    sourceSets {
        getByName("debug").assets.directories.add("src/pilot/assets")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

tasks.matching { it.name == "prePilotBuild" }.configureEach {
    doFirst {
        check(pilotSigning != null) {
            "A signed pilot build requires -PnamaztimePilotSigningProperties=/absolute/path/to/pilot-signing.properties"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.datastore:datastore-preferences-core:1.2.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.work:work-testing:2.11.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.84")
}
