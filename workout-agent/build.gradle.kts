plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.workoutapp.agent.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage"))
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.sqlite.jdbc)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
