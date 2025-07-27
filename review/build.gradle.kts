plugins {
  kotlin("jvm")
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":translation"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation(platform("com.aallam.openai:openai-client-bom:3.5.1"))
  implementation("com.aallam.openai:openai-client")
  implementation("com.aallam.ktoken:ktoken:0.4.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
  testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
  useJUnitPlatform()
}

tasks.register<JavaExec>("runReview") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunReviewKt")
}