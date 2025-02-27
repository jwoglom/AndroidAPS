plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

// buildscript {
//     ext {
//         jwoglom_pumpx2_version = 'v1.2.9'
//     }
// }

android {

    namespace = "app.aaps.pump.tandem"

    // defaultConfig {
    //     kapt {
    //         arguments {
    //             arg("room.incremental", "true")
    //             arg("room.schemaLocation", "$projectDir/schemas")
    //         }
    //     }
    // }
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

    implementation("com.github.jwoglom.pumpX2:pumpx2-android:v1.5.5")
    //implementation("com.github.jwoglom.pumpX2:pumpx2-messages:v1.5.0")
    //implementation("com.github.jwoglom.pumpX2:pumpx2-shared:v1.5.0")

    // needed by X2
    implementation("com.github.weliem:blessed-android:2.4.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    // implementation 'com.github.weliem:blessed-android:2.4.0'
    // implementation 'com.jakewharton.timber:timber:5.0.1'
    // implementation "me.champeau.openbeans:openbeans:1.0.2"
    // implementation "commons-codec:commons-codec:1.15"
    // implementation "org.apache.commons:commons-lang3:3.12.0"
    // implementation "com.google.guava:guava:31.0.1-android"
    // implementation 'org.bouncycastle:bcprov-jdk14:1.77'

    // com.github.jwoglom.pumpX2:pumpx2-android:v1.4.5
    // com.github.jwoglom.pumpX2:pumpx2-messages:v1.4.5
    // com.github.jwoglom.pumpX2:pumpx2-cliparser:v1.4.5
    // com.github.jwoglom.pumpX2:pumpx2-shared:v1.4.5
}
