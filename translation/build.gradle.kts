import java.io.ByteArrayOutputStream

plugins {
  kotlin("jvm")
  application
  `java-test-fixtures`
  alias(libs.plugins.dokka)
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

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json.core)
  implementation(libs.ai.services)

  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.junit.jupiter.engine)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
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
  repositories {
    maven {
      name = "nexus"
      url = uri("https://maven.fvlaenix.com/repository/maven-releases/")
      credentials {
        username = System.getenv("NEXUS_USERNAME")
        password = System.getenv("NEXUS_PASSWORD")
      }
    }
  }

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
