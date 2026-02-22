    import java.util.Properties
    import java.io.FileInputStream
    import com.android.build.api.dsl.ApplicationExtension
    
    plugins {
        alias(libs.plugins.android.application)
        alias(libs.plugins.kotlin.android)
        alias(libs.plugins.kotlin.compose)
        alias(libs.plugins.ksp)
        alias(libs.plugins.aboutlibraries)
    }
    
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }
    val invidiousUrl = localProperties.getProperty("INVIDIOUS_URL") ?: "https://invidious.io"
    val githubToken = localProperties.getProperty("GITHUB_TOKEN") ?: ""
    
    extensions.configure<ApplicationExtension> {
        namespace = "com.alananasss.kittytune"
        compileSdk = 36
    
        defaultConfig {
            applicationId = "com.alananasss.kittytune.debug"
            minSdk = 26
            targetSdk = 36
            versionCode = 1
            versionName = "2.19.3"
    
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            buildConfigField("String", "MY_INVIDIOUS_URL", "\"$invidiousUrl\"")
            buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
        }
    
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
            isCoreLibraryDesugaringEnabled = true
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    
        buildFeatures {
            compose = true
            buildConfig = true
        }
    }
    
    dependencies {
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.androidx.activity.compose)
        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.compose.ui)
        implementation(libs.androidx.compose.ui.graphics)
        implementation(libs.androidx.compose.ui.tooling.preview)
        implementation(libs.androidx.compose.material3)
        implementation(libs.androidx.navigation.compose)
        implementation(libs.androidx.material.icons.extended)
        implementation(libs.coil.compose)
        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.session)
        implementation(libs.androidx.media3.exoplayer.hls)
        implementation(libs.retrofit)
        implementation(libs.retrofit.gson)
        implementation(libs.okhttp)
        implementation(libs.logging.interceptor)
        implementation(libs.androidx.palette.ktx)
        implementation(libs.androidx.media)
        implementation(libs.mp3agic)
        implementation(libs.androidx.glance.appwidget)
        implementation(libs.androidx.glance.material3)
        implementation(libs.compose.markdown)
        implementation(libs.newpipe.extractor)
        coreLibraryDesugaring(libs.desugar.jdk.libs)
        implementation(libs.innertune)
        implementation(libs.androidx.webkit)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines.guava)
        implementation(libs.reorderable)
        implementation(libs.androidx.room.runtime)
        implementation(libs.androidx.room.ktx)
        ksp(libs.androidx.room.compiler)
        implementation(libs.aboutlibraries.core)
        implementation(libs.aboutlibraries.compose)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.json)
        implementation(libs.ktor.client.encoding)
        implementation(project(":kizzy"))
        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(platform(libs.androidx.compose.bom))
        androidTestImplementation(libs.androidx.compose.ui.test.junit4)
        debugImplementation(libs.androidx.compose.ui.tooling)
        debugImplementation(libs.androidx.compose.ui.test.manifest)
    }
    
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    


