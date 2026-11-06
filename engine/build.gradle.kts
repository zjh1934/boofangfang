plugins { id("com.android.library") }
android {
    namespace = "com.boofangfang.engine"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17 -O3 -fexceptions -frtti")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    externalNativeBuild { cmake { path = File("src/main/cpp/CMakeLists.txt") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("org.json:json:20240303") }
