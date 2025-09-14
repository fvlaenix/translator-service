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

  implementation("io.grpc:grpc-kotlin-stub:1.4.0")
  implementation("com.google.protobuf:protobuf-java:3.16.3")
  implementation("com.google.protobuf:protobuf-kotlin:3.24.4")
  runtimeOnly("io.grpc:grpc-netty-shaded:1.59.0")
  implementation("io.grpc:grpc-protobuf:1.59.0")
  implementation("io.grpc:grpc-stub:1.59.0")
  compileOnly("org.apache.tomcat:annotations-api:6.0.53")

  implementation("org.apache.logging.log4j:log4j-core:2.20.0")
  implementation("org.apache.logging.log4j:log4j-api:2.20.0")

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
    artifact = "com.google.protobuf:protoc:3.24.4"
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:1.59.0"
    }
    create("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.0:jdk8@jar"
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