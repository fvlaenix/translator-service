import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.protobuf.gradle.id

plugins {
  kotlin("jvm")
  id("com.google.protobuf")
  id("com.github.johnrengelman.shadow")
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":translation"))
  implementation(libs.ai.services)

  implementation(libs.grpc.kotlin.stub)
  implementation(libs.protobuf.java)
  implementation(libs.protobuf.kotlin)
  runtimeOnly(libs.grpc.netty.shaded)
  implementation(libs.grpc.protobuf)
  implementation(libs.grpc.stub)
  compileOnly(libs.tomcat.annotations.api)

  implementation(libs.log4j.core)
  implementation(libs.log4j.api)

  protobuf(files("../discord-bots-rpc/gpt-request.proto", "../discord-bots-rpc/is-alive.proto"))
}

application {
  mainClass.set("RunServerKt")
}

tasks.register<JavaExec>("runServer") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("RunServerKt")
}

tasks.register<ShadowJar>("runServerJar") {
  mergeServiceFiles()
  group = "shadow"
  description = "Run server runServer"

  from(sourceSets.main.get().output)
  from(project.configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
  exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
  configurations = listOf(project.configurations.runtimeClasspath.get())

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  archiveFileName.set("runServer.jar")
  manifest {
    attributes["Main-Class"] = "RunServerKt"
  }
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${libs.versions.protobufKotlin.get()}"
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
    }
    create("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpcKotlin.get()}:jdk8@jar"
    }
  }
  generateProtoTasks {
    all().forEach {
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins {
        create("kotlin")
      }
    }
  }
}
