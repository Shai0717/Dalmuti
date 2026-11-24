plugins {
    kotlin("jvm") version "2.3.20"
}

group = "kr.dalmuti"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.254-stable")
    compileOnly("io.github.Shai0717:Kolor:1.0.0")
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

tasks.register<JavaExec>("modelTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("kr.dalmuti.game.RulesTestKt")
}

tasks.check {
    dependsOn("modelTest")
}

tasks.test {
    failOnNoDiscoveredTests = false
}
