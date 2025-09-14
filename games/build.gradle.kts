plugins {
  kotlin("jvm")
  application
  `java-test-fixtures`
}

dependencies {
  implementation(project(":core"))
  implementation("ai-services:core")
  implementation(project(":translation"))

  implementation("io.github.evanrupert:excelkt:1.0.2")

  implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

  // Apache POI dependencies
  implementation("org.apache.poi:poi:5.2.3")
  implementation("org.apache.poi:poi-ooxml:5.2.3")
  implementation("org.apache.commons:commons-compress:1.26.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
  testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
  testImplementation(testFixtures(project(":core")))
  testImplementation(testFixtures(project(":translation")))
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