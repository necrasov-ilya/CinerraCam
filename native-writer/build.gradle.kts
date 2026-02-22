plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val enableNativeWriter = (findProperty("enableNativeWriter") as String?)?.toBoolean() ?: false

android {
    namespace = "com.cinerracam.nativewriter"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        if (enableNativeWriter) {
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++17"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    if (enableNativeWriter) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
}
