plugins {
  kotlin("jvm")
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":translation"))
  implementation(libs.ai.services)

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json.core)
  implementation(platform(libs.openai.client.bom))
  implementation(libs.openai.client)
  implementation(libs.ktoken)

  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.junit.jupiter.engine)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
  useJUnitPlatform()
}

tasks.register<JavaExec>("runReview") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunReviewKt")
}
