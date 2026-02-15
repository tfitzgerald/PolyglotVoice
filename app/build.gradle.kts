plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.polyglotvoice'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.polyglotvoice"
        minSdk 24 // Required for ML Kit and modern TTS features
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = '1.8'
    }

    // Ensures that the layout editor and build system see your resources correctly
    buildFeatures {
        viewBinding true
    }
}

dependencies {
    // Core Android libraries
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // ML Kit Translation (Natural Language Processing)
    // This provides the offline translation models
    implementation 'com.google.mlkit:translate:17.0.1'

    // ML Kit Language Identification
    // This allows the app to distinguish between English and Spanish automatically
    implementation 'com.google.mlkit:language-id:17.0.4'

    // Unit Testing
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}