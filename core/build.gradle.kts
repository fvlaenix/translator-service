plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  `java-test-fixtures`
}

dependencies {
  implementation(platform("com.aallam.openai:openai-client-bom:3.5.1"))
  implementation("com.aallam.openai:openai-client")
  implementation("com.aallam.ktoken:ktoken:0.4.0")
  runtimeOnly("io.ktor:ktor-client-okhttp")

  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

  implementation("org.slf4j:slf4j-api:2.0.9")
  implementation("org.slf4j:slf4j-simple:2.0.9")
  implementation("org.apache.logging.log4j:log4j-core:2.20.0")
  implementation("org.apache.logging.log4j:log4j-api:2.20.0")
}