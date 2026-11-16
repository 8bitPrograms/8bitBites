plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.eightbit.bite"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.eightbit.bite"
        minSdk = 28
        targetSdk = 36
        versionCode = 16
        versionName = "0.10.0-dev"
        val repository = providers.gradleProperty("bite.repository").getOrElse("8bitPrograms/8bitBites")
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repository))
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$repository\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(17) }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

val catalogueAssets = tasks.register<Sync>("prepareCatalogueAssets") {
    from(rootProject.file("database/foods.sqlite"))
    from(rootProject.file("database/NOTICE.md"))
    into(layout.buildDirectory.dir("generated/catalogueAssets/catalogue"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/catalogueAssets"))
android.sourceSets.getByName("test").resources.srcDir("schemas")
tasks.named("preBuild").configure { dependsOn(catalogueAssets) }

dependencies {
    implementation(project(":core"))
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    implementation("androidx.camera:camera-camera2:1.5.1")
    implementation("androidx.camera:camera-lifecycle:1.5.1")
    implementation("androidx.camera:camera-view:1.5.1")
    // LiteRT-LM brings this at runtime; CameraX also needs its ListenableFuture API at compile time.
    implementation("com.google.guava:guava:31.1-android")
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    kapt("androidx.room:room-compiler:2.8.3")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
