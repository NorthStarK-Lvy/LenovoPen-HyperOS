plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.myrom.lenovopen"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.myrom.lenovopen"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.animation:animation:1.11.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
}

// Android variant unit-test tasks are not enabled by this AGP configuration.
// Keep the pure policy checks runnable on any host without an Android device.
val policyTestRuntime by configurations.creating
dependencies { add(policyTestRuntime.name, "junit:junit:4.13.2") }
val compilePenPolicyTests by tasks.registering(JavaCompile::class) {
    source("src/main/java/io/github/myrom/lenovopen/Protocol.java")
    source("src/main/java/io/github/myrom/lenovopen/PenIdentity.java")
    source("src/test/java/io/github/myrom/lenovopen/PenIdentityTest.java")
    destinationDirectory.set(layout.buildDirectory.dir("policy-tests/classes"))
    classpath = policyTestRuntime
    options.encoding = "UTF-8"
    options.release.set(17)
}
tasks.register<Test>("penPolicyTest") {
    dependsOn(compilePenPolicyTests)
    testClassesDirs = files(compilePenPolicyTests.flatMap { it.destinationDirectory })
    classpath = files(compilePenPolicyTests.flatMap { it.destinationDirectory }) + policyTestRuntime
    useJUnit()
    maxHeapSize = "256m"
    jvmArgs("-XX:+UseSerialGC")
}
