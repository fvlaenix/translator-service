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

fun Project.resolveVersion(): String {
  val cli = findProperty("releaseVersion") as String?
  if (!cli.isNullOrBlank()) return cli

  val tagPattern = Regex("^v\\d+\\.\\d+\\.\\d+$")
  val envTag = sequenceOf(
    System.getenv("GIT_TAG"),
    System.getenv("GITHUB_REF_NAME")
  ).firstOrNull { it != null && tagPattern.matches(it) }

  if (envTag != null) return envTag.removePrefix("v")

  return "0.0.0-SNAPSHOT"
}

val resolvedVersion = project.resolveVersion()

allprojects {
  group = "com.github.fvlaenix"
  version = resolvedVersion
}

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
