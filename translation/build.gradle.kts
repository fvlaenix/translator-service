plugins {
  kotlin("jvm")
  application
  `java-test-fixtures`
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

tasks.register<JavaExec>("runTxt") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunTxtKt")
}

tasks.register<JavaExec>("runUpgradedTxt") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunUpgradedTxtKt")
}