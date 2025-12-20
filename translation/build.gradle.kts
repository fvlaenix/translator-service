import java.io.ByteArrayOutputStream

plugins {
  kotlin("jvm")
  application
  `java-test-fixtures`
  id("org.jetbrains.dokka") version "2.0.0"
  `maven-publish`
}

fun gitExactTag(): String? {
  val stdout = ByteArrayOutputStream()
  val stderr = ByteArrayOutputStream()
  val result = exec {
    commandLine("git", "describe", "--tags", "--exact-match")
    standardOutput = stdout
    errorOutput = stderr
    isIgnoreExitValue = true
  }
  return if (result.exitValue == 0) {
    stdout.toString().trim().ifBlank { null }
  } else {
    null
  }
}

fun resolveVersion(): String {
  val cliVersion = (findProperty("version") as? String)
    ?.takeIf { it.isNotBlank() && it != "unspecified" }
  val normalizedCliVersion = when {
    cliVersion == null -> null
    cliVersion.matches(Regex("v\\d+\\.\\d+\\.\\d+")) -> cliVersion.removePrefix("v")
    cliVersion.matches(Regex("\\d+\\.\\d+\\.\\d+")) -> cliVersion
    else -> null
  }
  if (normalizedCliVersion != null) {
    return normalizedCliVersion
  }

  val envTag = System.getenv("GIT_TAG")
    ?: System.getenv("GITHUB_REF_NAME")
  val tag = envTag?.takeIf { it.matches(Regex("v\\d+\\.\\d+\\.\\d+")) }
    ?: gitExactTag()
  return tag?.removePrefix("v") ?: "0.0.0-SNAPSHOT"
}

group = (findProperty("group") as? String) ?: "com.github.fvlaenix"
version = resolveVersion()

dependencies {
  implementation(project(":core"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation(libs.ai.services)

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
  testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
  testFixturesImplementation(testFixtures(project(":core")))
}

tasks.test {
  useJUnitPlatform()
}

val sourcesJar by tasks.registering(Jar::class) {
  archiveClassifier.set("sources")
  from(sourceSets.main.get().allSource)
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
  archiveClassifier.set("javadoc")
  from(tasks.named("dokkaJavadoc"))
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
      artifact(sourcesJar)
      artifact(dokkaJavadocJar)
      artifactId = "translator-service"
    }
  }
}

tasks.register<JavaExec>("runTxt") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunTxtKt")
}

tasks.register<JavaExec>("runUpgradedTxt") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunUpgradedTxtKt")
}
