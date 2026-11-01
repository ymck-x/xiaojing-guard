import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.fangchenmi.guard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fangchenmi.guard"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.3.5"
        // 付费版打包时用 -Plicensee="买家昵称" 注入授权信息；免费版为空。
        val licensee = (project.findProperty("licensee") as String?) ?: ""
        buildConfigField("String", "LICENSEE", "\"$licensee\"")
    }

    signingConfigs {
        // 自签名发布：家长直接侧载安装，不需要 Play 签名。
        create("release") {
            val keystoreFile = rootProject.file("keystore/fangchenmi.jks")
            if (keystoreFile.exists()) {
                val props = Properties().apply {
                    val f = rootProject.file("keystore/keystore.properties")
                    if (f.exists()) f.inputStream().use { load(it) }
                }
                storeFile = keystoreFile
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    // 免费版 / 付费版：同包名、同签名，付费版可直接覆盖安装免费版，数据保留。
    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            buildConfigField("boolean", "PRO", "false")
            versionNameSuffix = "-free"
        }
        create("pro") {
            dimension = "tier"
            buildConfigField("boolean", "PRO", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (rootProject.file("keystore/fangchenmi.jks").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
