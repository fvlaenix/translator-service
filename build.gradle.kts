import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.google.protobuf") version "0.9.4"
    application
}

group = "com.fvlaenix"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.grpc:grpc-kotlin-stub:1.4.0")
    implementation("com.google.protobuf:protobuf-java:3.16.3")
    implementation("com.google.protobuf:protobuf-kotlin:3.24.4")
    runtimeOnly("io.grpc:grpc-netty-shaded:1.59.0")
    implementation("io.grpc:grpc-protobuf:1.59.0")
    implementation("io.grpc:grpc-stub:1.59.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    protobuf(files("rpc/translator/"))

    implementation(platform("com.aallam.openai:openai-client-bom:3.5.1"))
    implementation("com.aallam.openai:openai-client")
    runtimeOnly("io.ktor:ktor-client-okhttp")

    implementation("io.github.evanrupert:excelkt:1.0.2")

    testImplementation("junit:junit:4.4")
}

kotlin {
    jvmToolchain(13)
}

application {
    mainClass.set("MainKt")
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
