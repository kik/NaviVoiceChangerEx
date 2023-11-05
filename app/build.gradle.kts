plugins {
    id("com.android.application")
    id("com.google.protobuf") version "0.9.4"
}

android {
    namespace = "io.github.kik.navivoicechangerex"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.kik.navivoicechangerex"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            //excludes += "**"
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.0"
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.google.protobuf:protobuf-javalite:3.25.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.arnx:jsonic:1.3.0")

    implementation("com.fasterxml.jackson.core:jackson-core:2.15.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.3")

    compileOnly("io.github.libxposed:api:100")
    implementation("io.github.libxposed:service:100-1.0.0")
}