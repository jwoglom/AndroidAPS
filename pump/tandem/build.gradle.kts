plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
    alias(libs.plugins.compose.compiler)
}

// buildscript {
//     ext {
//         jwoglom_pumpx2_version = 'v1.2.9'
//         compose_version = '1.3.1'
//     }
// }

android {

    namespace = "app.aaps.pump.tandem"

    defaultConfig {
        ksp {
            arg("room.incremental", "true")
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }


    buildFeatures {
        compose=true
    }

    composeOptions {
        kotlinCompilerExtensionVersion="1.5.3"
    }
}


dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:libraries"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":core:keys"))
    implementation(project(":implementation"))

    implementation(project(":pump:common"))
    implementation(project(":shared:tests"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))


    api(libs.androidx.room)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.rxjava3)

    ksp(libs.androidx.room.compiler)
    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)

    // compose dependencies
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.io.github.vanpra.compose.dialogs.datetime)

    // pumpX2
    implementation(libs.com.jakewharton.timber)
    implementation(libs.com.github.weliem.blessed.android)
    //implementation("com.github.jwoglom.pumpX2:pumpx2-android:v1.6.9")
    implementation(libs.com.github.jwoglom.pumpx2.android)


    // temporarily X2 released under atech-software instead of jwoglom (some problems with linking)
    // implementation("com.atech-software.pumpX2:pumpx2-android:v1.4.4.0")
    // implementation("com.atech-software.pumpX2:pumpx2-messages:v1.4.4.0")
    // implementation("com.atech-software.pumpX2:pumpx2-shared:v1.4.4.0")


//    implementation("com.github.jwoglom.pumpX2:pumpx2-messages:v1.5.10")
//    implementation("com.github.jwoglom.pumpX2:pumpx2-shared:v1.5.10")


}
