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

    implementation(project(":pump:pump-common"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":shared:impl"))

    // ? api(Libs.AndroidX.fragment)
    // ? api(Libs.AndroidX.navigationFragment)

    //api(Libs.AndroidX.Room.room)
    //api(Libs.AndroidX.Room.runtime)
    //api(Libs.AndroidX.Room.rxJava3)
    //kapt(Libs.AndroidX.Room.compiler)

    //api(libs.com.google.android.material)

    api(libs.androidx.fragment)

    //implementation ("androidx.viewpager2:viewpager2:1.0.0")

    //api(libs.org.b)

    api(libs.androidx.room)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.rxjava3)
    api(libs.commons.codec)

    ksp(libs.androidx.room.compiler)
    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)

    //implementation ("com.tbuonomo.andrui:viewpagerdotsindicator:4.3")

    // implementation ("com.github.badoualy:stepper-indicator:1.0.7") {
    //     exclude(group = "com.android.support", module = "appcompat-v7")
    // }

    // <dependency>
    // <groupId>com.android.support</groupId>
    // <artifactId>appcompat-v7</artifactId>
    // <version>26.0.0-beta2</version>
    // <scope>compile</scope>
    // </dependency>


    // temporarily X2 released under atech-software instead of jwoglom (some problems with linking)
    // implementation("com.atech-software.pumpX2:pumpx2-android:v1.4.4.0")
    // implementation("com.atech-software.pumpX2:pumpx2-messages:v1.4.4.0")
    // implementation("com.atech-software.pumpX2:pumpx2-shared:v1.4.4.0")

    implementation("com.github.jwoglom.pumpX2:pumpx2-android:v1.6.8")
//    implementation("com.github.jwoglom.pumpX2:pumpx2-messages:v1.5.10")
//    implementation("com.github.jwoglom.pumpX2:pumpx2-shared:v1.5.10")

    // needed by X2
    implementation("com.github.weliem:blessed-android:2.4.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    //implementation("org.apache.commons:commons-lang3:3.12.0")

    // compose dependencies are just testing ones, final set will be determined in phase 2
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    //implementation("androidx.activity:activity-compose:1.10.1")
    //implementation("androidx.compose:compose-bom")


    //implementation("androidx.compose.ui:ui:1.3.1")
    //implementation("androidx.compose.ui:ui-tooling:1.3.1")
    //implementation("androidx.compose.ui:ui-tooling-preview:1.3.1")
    //implementation("androidx.compose.material3:material3:1.1.0-alpha03")
    implementation("androidx.navigation:navigation-compose:2.5.3")
    implementation("androidx.compose.material:material:1.3.1")
    implementation("androidx.compose.runtime:runtime-livedata:1.3.1")
}
