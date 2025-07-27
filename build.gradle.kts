plugins {
  kotlin("jvm") version "1.9.0" apply false
  id("com.google.protobuf") version "0.9.4" apply false
  kotlin("plugin.serialization") version "1.9.24" apply false
  id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

group = "com.fvlaenix"
version = "1.0-SNAPSHOT"

subprojects {
  repositories {
    mavenCentral()
  }
}