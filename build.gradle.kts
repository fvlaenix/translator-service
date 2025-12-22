tasks.register("publishToMavenLocal") {
  group = "publishing"
  description = "Publishes the translation library to Maven Local."
  dependsOn(":translation:publishToMavenLocal")
}
