plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  `java-test-fixtures`
}

dependencies {
  implementation(libs.ai.services)
  api(platform(libs.openai.client.bom))
  api(libs.openai.client)
  implementation(libs.ktoken)
  runtimeOnly(libs.ktor.client.okhttp)

  implementation(libs.kotlinx.serialization.json.core)

  implementation(libs.bundles.logging)

  testFixturesImplementation(libs.ai.services)
  testFixturesApi(platform(libs.openai.client.bom))
  testFixturesApi(libs.openai.client)
}
