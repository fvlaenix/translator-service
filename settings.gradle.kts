// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

import me.champeau.gradle.igp.gitRepositories
import java.util.*

pluginManagement {
  repositories { gradlePluginPortal(); mavenCentral() }
}

dependencyResolutionManagement {
  // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
  kotlin("jvm") version "1.9.0" apply false
  id("com.google.protobuf") version "0.9.4" apply false
  kotlin("plugin.serialization") version "1.9.24" apply false
  id("com.github.johnrengelman.shadow") version "8.1.1" apply false

  id("me.champeau.includegit") version "0.3.0"
}

include(":core")
include(":translation")
include(":games")
include(":review")
include(":server")

rootProject.name = "translator-service"


val localOverrides = Properties().also { p ->
  val f = rootDir.resolve("local.gradle.properties")
  if (f.exists()) f.inputStream().use { p.load(it) }
  else println("No local.gradle.properties file found")
}

fun overrideUrl(name: String): String? =
  localOverrides.getProperty("repository.$name.url")?.trim()?.takeIf { it.isNotEmpty() }

fun overridePath(name: String): String? =
  localOverrides.getProperty("repository.$name.path")?.trim()?.takeIf { it.isNotEmpty() }

fun overrideBranch(name: String): String? =
  localOverrides.getProperty("repository.$name.branch")?.trim()?.takeIf { it.isNotEmpty() }

val defaultCheckoutsDir: File = rootDir.resolve(".").absoluteFile

fun runGit(vararg args: String, workDir: File? = null) {
  val pb = ProcessBuilder(listOf("git") + args)
  if (workDir != null) pb.directory(workDir)
  pb.redirectErrorStream(true)
  val proc = pb.start()
  val out = proc.inputStream.bufferedReader().readText()
  val code = proc.waitFor()
  if (code != 0) {
    throw RuntimeException("git ${args.joinToString(" ")} failed with code $code\n$out")
  }
}

// "Refresh clones" flag at startup (optional: ./gradlew -Drefresh.git.repositories)
val refreshRepos = (System.getProperty("refresh.git.repositories") ?: "false").toBooleanStrictOrNull() == true

fun ensureNonBuildCheckout(
  name: String,
  defaultUrl: String,
  defaultBranch: String
): File {
  // 1) If a local path is specified — just use it
  overridePath(name)?.let { p ->
    val dir = File(p).let { if (it.isAbsolute) it else File(rootDir, p) }.absoluteFile
    require(dir.exists()) { "Non-build repo '$name' path not found: $dir" }
    return dir
  }

  // 2) Otherwise, clone/update into defaultCheckoutsDir/name
  val url = overrideUrl(name) ?: defaultUrl
  val branch = overrideBranch(name) ?: defaultBranch
  val checkoutDir = defaultCheckoutsDir.resolve(name).absoluteFile

  if (!checkoutDir.exists()) {
    checkoutDir.parentFile.mkdirs()
    println("Cloning $url with branch $branch into ${checkoutDir.absolutePath}")
    runGit("clone", "--branch", branch, url, checkoutDir.absolutePath)
  } else if (refreshRepos) {
    // refresh: fetch + checkout branch + pull
    runGit("-C", checkoutDir.absolutePath, "fetch", "--all", "--prune")
    runGit("-C", checkoutDir.absolutePath, "checkout", branch)
    runGit("-C", checkoutDir.absolutePath, "pull", "--ff-only")
  }
  return checkoutDir
}

data class RepoSpec(
  val name: String,
  val defaultUrl: String,
  val branch: String,
  val isNonBuild: Boolean = false,
  val substitutions: Map<String, String> = emptyMap()
)

val repos = listOf(
  RepoSpec(
    name = "discord-bots-rpc",
    branch = "master",
    defaultUrl = "https://github.com/fvlaenix/discord-bots-rpc.git",
    isNonBuild = true
  ),
  RepoSpec(
    name = "ai-services",
    branch = "master",
    defaultUrl = "https://github.com/fvlaenix/ai-services.git",
    isNonBuild = false
  )
)

repos.filter { it.isNonBuild }.forEach { spec ->
  val dir = ensureNonBuildCheckout(spec.name, spec.defaultUrl, spec.branch)
  System.setProperty("repo.${spec.name}.dir", dir.absolutePath)
}

gitRepositories {
  useGitCli = true
  checkoutsDirectory = defaultCheckoutsDir

  repos.filter { !it.isNonBuild }.forEach { spec ->
    val localPath = overridePath(spec.name)?.let { p ->
      File(p).let { if (it.isAbsolute) it else File(rootDir, p) }.absoluteFile
    }
    if (localPath != null && localPath.exists()) {
      includeBuild(localPath) {
        dependencySubstitution {
          spec.substitutions.forEach { (gav, projPath) ->
            substitute(module(gav)).using(project(projPath))
          }
        }
      }
    } else {
      include(spec.name) {
        uri = overrideUrl(spec.name) ?: spec.defaultUrl
        branch = overrideBranch(spec.name) ?: spec.branch

        includeBuild {
          dependencySubstitution {
            spec.substitutions.forEach { (gav, projPath) ->
              substitute(module(gav)).using(project(projPath))
            }
          }
        }
      }
    }
  }
}
