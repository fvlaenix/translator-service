# Translation service

## How to clone

Simple clone for usage:

```shell
git clone --recurse-submodules https://github.com/fvlaenix/translator-service.git
```

Dev build clone:

```shell
git clone --recurse-submodules \
  --config url."ssh://git@github.com/fvlaenix/".insteadof=https://github.com/fvlaenix/ \
  https://github.com/fvlaenix/translator-service.git
```


[Install instructions](doc/install.md)

## JitPack (translation library)

### Consume from Gradle (Kotlin DSL)

```kotlin
repositories {
  mavenCentral()
  maven("https://jitpack.io")
}

dependencies {
  implementation("com.github.fvlaenix:translator-service:vX.Y.Z")
}
```

### Release (maintainers)

1. Open GitHub Actions and run the `Release` workflow.
2. Provide either a `tag` like `v1.2.3` or choose a `bump` (patch/minor/major).
3. The workflow pushes the tag; JitPack builds from that tag on first request.
