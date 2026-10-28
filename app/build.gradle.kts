plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val uploadKeystorePath = providers.environmentVariable("OPENDISPLAY_UPLOAD_KEYSTORE").orNull
val uploadKeystorePassword = providers.environmentVariable("OPENDISPLAY_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("OPENDISPLAY_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("OPENDISPLAY_UPLOAD_KEY_PASSWORD").orNull
val hasUploadSigning = listOf(
    uploadKeystorePath,
    uploadKeystorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "im.amomo.opendisplay"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "im.amomo.opendisplay"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    signingConfigs {
        if (hasUploadSigning) {
            create("playUpload") {
                storeFile = file(uploadKeystorePath!!)
                storePassword = uploadKeystorePassword!!
                keyAlias = uploadKeyAlias!!
                keyPassword = uploadKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            if (hasUploadSigning) {
                signingConfig = signingConfigs.getByName("playUpload")
            }
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
    buildFeatures {
        compose = true
    }
}

if (!hasUploadSigning) {
    tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }
        .configureEach {
            doFirst {
                error(
                    "Release signing is not configured. Source ~/projects/android/kp/open-display-upload-key.env " +
                        "or set OPENDISPLAY_UPLOAD_KEYSTORE, OPENDISPLAY_UPLOAD_STORE_PASSWORD, " +
                        "OPENDISPLAY_UPLOAD_KEY_ALIAS, and OPENDISPLAY_UPLOAD_KEY_PASSWORD."
                )
            }
        }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
