plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.alyssum.relegion"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.alyssum.relegion"
        minSdk = 33
        targetSdk = 35
        versionCode = 34
        versionName = "1.3.2"
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
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("no.nordicsemi.android:dfu:2.11.0")
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    testImplementation("junit:junit:4.13.2")
}

// Android variant unit-test tasks are not enabled by this AGP configuration.
// Keep the pure policy checks runnable on any host without an Android device.
val policyTestRuntime by configurations.creating
val policyAndroidClasspath = androidComponents.sdkComponents.bootClasspath
dependencies {
    add(policyTestRuntime.name, "junit:junit:4.13.2")
    add(policyTestRuntime.name, "org.json:json:20240303")
}
val compilePenPolicyTests by tasks.registering(JavaCompile::class) {
    source("src/main/java/com/alyssum/relegion/Protocol.java")
    source("src/main/java/com/alyssum/relegion/PenIdentity.java")
    source("src/main/java/com/alyssum/relegion/PenBatteryCache.java")
    source("src/main/java/com/alyssum/relegion/PenModel.java")
    source("src/main/java/com/alyssum/relegion/PenActions.java")
    source("src/main/java/com/alyssum/relegion/LenovoFirmwareClient.java")
    source("src/main/java/com/alyssum/relegion/LenovoFirmwarePackage.java")
    source("src/main/java/com/alyssum/relegion/LogLevel.java")
    source("src/main/java/com/alyssum/relegion/tablet/ModelNamePolicy.java")
    source("src/main/java/com/alyssum/relegion/hook/GameRefreshPolicy.java")
    source(fileTree("src/main/java/com/alyssum/relegion/hyperconnect/xposed") {
        include("ChannelLimitPolicy.java", "HostBindingRepair.java", "NotificationClickSession.java",
            "TargetProcess.java", "CompatibilityPolicy.java", "HookFeatureFlags.java")
    })
    source("src/main/java/com/alyssum/relegion/hyperconnect/config/FeaturePreferences.java")
    source("src/test/java/com/alyssum/relegion/PenIdentityTest.java")
    source("src/test/java/com/alyssum/relegion/PenBatteryCacheTest.java")
    source("src/test/java/com/alyssum/relegion/PenModelTest.java")
    source("src/test/java/com/alyssum/relegion/PenActionsTest.java")
    source("src/test/java/com/alyssum/relegion/LenovoFirmwareClientTest.java")
    source("src/test/java/com/alyssum/relegion/LenovoFirmwarePackageTest.java")
    source("src/test/java/com/alyssum/relegion/LogLevelTest.java")
    source("src/test/java/com/alyssum/relegion/ModelNamePolicyTest.java")
    source("src/test/java/com/alyssum/relegion/hook/GameRefreshPolicyTest.java")
    source(fileTree("src/test/java/com/alyssum/relegion/hyperconnect") { include("**/*.java") })
    destinationDirectory.set(layout.buildDirectory.dir("policy-tests/classes"))
    classpath = policyTestRuntime + files(policyAndroidClasspath)
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
