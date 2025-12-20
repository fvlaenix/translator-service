// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

pluginManagement {
  repositories { gradlePluginPortal(); mavenCentral() }
}

dependencyResolutionManagement {
  // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
    maven("https://jitpack.io")
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
  kotlin("jvm") version "2.2.20-RC2" apply false
  id("com.google.protobuf") version "0.9.5" apply false
  kotlin("plugin.serialization") version "2.2.20-RC2" apply false
  id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

include(":core")
include(":translation")
include(":games")
include(":review")
include(":server")

rootProject.name = "translator-service"
