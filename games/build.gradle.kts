plugins {
  kotlin("jvm")
  application
  `java-test-fixtures`
}

dependencies {
  implementation(project(":core"))
  implementation(libs.ai.services)
  implementation(project(":translation"))

  implementation(libs.excelkt)

  implementation(libs.bundles.jackson)
  implementation(libs.kotlinx.coroutines.core)

  // Apache POI dependencies
  implementation(libs.bundles.poi)

  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.junit.jupiter.engine)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(testFixtures(project(":core")))
  testImplementation(testFixtures(project(":translation")))

  // Additional test dependencies for comprehensive testing
  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
}

tasks.test {
  useJUnitPlatform()
}

tasks.register<JavaExec>("runBook") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunBookKt")
}

tasks.register<JavaExec>("runSRPG") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunSRPGKt")
}
