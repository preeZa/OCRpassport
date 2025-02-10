plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.ocrpassport"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ocrpassport"
        minSdk = 28

        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
//    buildFeatures {
//        compose = true
//    }

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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation (libs.androidx.material3)
    implementation (libs.androidx.activity.compose)
    implementation (libs.androidx.foundation)
    implementation (libs.androidx.ui)

    //real-time
    implementation (libs.androidx.camera.camera2)
    implementation (libs.androidx.camera.lifecycle)
    implementation (libs.androidx.camera.view)


    //ml kit
    implementation (libs.text.recognition)

    // json
    implementation (libs.gson)

    //scaner_code
    implementation (libs.code.scanner)

    //Readable Travel Document
    implementation ("net.sf.scuba:scuba-sc-android:0.0.18")
    implementation ("org.jmrtd:jmrtd:0.7.21")
//    implementation ("org.jmrtd:jnbis:1.0.4")
//    implementation ("com.atos.mediagenerator:jj2000:5.2")

    implementation(project(":openCV"))

    implementation (libs.lottie)

    implementation (libs.play.services.tasks)
    implementation (libs.kotlinx.coroutines.play.services)

}