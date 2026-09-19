plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.sqlite.jdbc)
}
