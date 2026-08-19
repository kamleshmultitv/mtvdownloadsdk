# JitPack Publish Guide

This is the repeatable release checklist for publishing the MTV Downloader SDK from this repo to JitPack.

## Current Repo Status

The downloader module is ready for JitPack publishing:

- Module: `:mtvdownloader`
- Publication plugin: `maven-publish`
- Group id: `com.github.kamleshmultitv`
- Artifact id: `mtvdownloader`
- Current artifact version/tag: `download-1.1.1`
- JDK: `openjdk17` in `jitpack.yml`
- JitPack install command: `sh gradlew :mtvdownloader:publishToMavenLocal`

`publishToMavenLocal` has been verified locally for `:mtvdownloader`.

## Files That Must Stay Correct

`jitpack.yml`:

```yaml
jdk:
  - openjdk17

install:
  - sh gradlew :mtvdownloader:publishToMavenLocal
```

`mtvdownloader/build.gradle.kts` must keep:

```kotlin
plugins {
    id("maven-publish")
}

android {
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.kamleshmultitv"
                artifactId = "mtvdownloader"
                version = "download-1.1.1"
            }
        }
    }
}
```

When releasing a new version, update the `version` value before tagging.

## Pre-Release Checks

Run these before creating the tag:

```bash
git status --short
```

```bash
git diff --check
```

```bash
sh gradlew :mtvdownloader:testDebugUnitTest :mtvdownloader:publishToMavenLocal
```

Optional full app check:

```bash
sh gradlew :app:compileDebugKotlin :app:assembleDebug
```

If any command fails, fix it before tagging. JitPack runs from GitHub, so only committed and pushed code is published.

## Release Commands

Use this flow for the current release:

```bash
git status --short
```

```bash
git add mtvdownloader gradle jitpack.yml README.md mtvdownloader/README.md monatization.md jitpack-publish-guide.md
```

Adjust the `git add` command if app/sample docs or handoff docs also belong in the release. Do not stage local APK outputs, local secrets, unrelated IDE files, or `google-services.json`.

```bash
git commit -m "Release downloader SDK download-1.1.1"
```

```bash
git tag download-1.1.1
```

```bash
git push origin main
```

```bash
git push origin download-1.1.1
```

Optional GitHub release command, if you use the GitHub CLI:

```bash
gh release create download-1.1.1 --title "download-1.1.1" --notes "MTV Downloader SDK release download-1.1.1"
```

If your branch is not `main`, replace `main` with the actual release branch.

## Trigger JitPack

After pushing the tag:

1. Open JitPack.
2. Search for this repository:

```text
https://github.com/kamleshmultitv/mtvdownloadsdk
```

3. Select the `download-1.1.1` tag.
4. Click `Get it`.
5. Open the build log and confirm `BUILD SUCCESSFUL`.

JitPack builds the project on demand the first time the dependency is requested, so the first resolve may take longer.

If a tag was already pushed and failed because of a bad `jitpack.yml`, do not assume JitPack will use your latest branch commit. A tag points to a specific commit. The clean fix is to commit the corrected `jitpack.yml`, bump the SDK version, and create a new tag. Only reuse the same tag if you deliberately delete/recreate the Git tag and remove/retry the failed JitPack build.

## Consumer Dependency

Add JitPack in the consuming app `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Use the dependency already documented by this repo:

```kotlin
dependencies {
    implementation("com.github.kamleshmultitv:mtvdownloader:download-1.1.1")
}
```

If JitPack exposes this repo as a multi-module build instead, use the module coordinate:

```kotlin
dependencies {
    implementation("com.github.kamleshmultitv.mtvdownloadsdk:mtvdownloader:download-1.1.1")
}
```

Check the JitPack page after the build; it shows the exact dependency string that resolved successfully.

## Snapshot Dependency For Testing

For temporary testing before a release tag, use a commit hash:

```kotlin
dependencies {
    implementation("com.github.kamleshmultitv:mtvdownloader:<commit-sha>")
}
```

Only use commit dependencies for testing. Production apps should use a release tag.

## Version Bump Checklist

For the next release:

1. Update `version = "download-x.y.z"` in `mtvdownloader/build.gradle.kts`.
2. Update install examples in `mtvdownloader/README.md`.
3. Update this file's current version and command examples.
4. Run pre-release checks.
5. Commit.
6. Tag with the same value, for example `download-x.y.z`.
7. Push branch and tag.
8. Trigger JitPack and verify the log.

Keep the Gradle publication version and Git tag the same. It makes dependency strings, support logs, and rollback much easier to reason about.

## Troubleshooting

| Problem | Check |
| --- | --- |
| JitPack cannot find the repo | Confirm the GitHub repo URL is correct and accessible to JitPack. |
| Error parsing yml config file / Error reading Map: build | Use top-level `install:` in `jitpack.yml`; do not use `build.commands`. |
| Same tag still fails after fixing `jitpack.yml` | Confirm the tag points to the commit containing the fixed file, or create a new tag. |
| JitPack builds the app instead of the SDK | Confirm `jitpack.yml` install step runs `sh gradlew :mtvdownloader:publishToMavenLocal`. |
| JDK mismatch | Confirm `jitpack.yml` uses `openjdk17`. |
| Dependency does not resolve | Confirm the tag was pushed, the JitPack build succeeded, and the consuming app has `maven("https://jitpack.io")`. |
| Wrong dependency coordinate | Use the dependency string shown on the JitPack repo page after a successful build. |
| Build fails on Room schema or kapt | Run `sh gradlew :mtvdownloader:publishToMavenLocal` locally and commit generated schema files if Room requires them. |
| Build succeeds locally but fails on JitPack | Open the JitPack build log; usually the issue is missing committed files, wrong JDK, unavailable dependency, or a command mismatch in `jitpack.yml`. |

## Files Not To Commit

Keep these files local and out of Git:

- `app/google-services.json`
- Any other `google-services.json`
- `local.properties`
- APK/AAB outputs under `app/release/` or module `build/` folders
- Local IDE state under `.idea/`

## Quick Commands

Current release:

```bash
git diff --check
sh gradlew :mtvdownloader:testDebugUnitTest :mtvdownloader:publishToMavenLocal
git add mtvdownloader gradle jitpack.yml README.md mtvdownloader/README.md monatization.md jitpack-publish-guide.md
git commit -m "Release downloader SDK download-1.1.1"
git tag download-1.1.1
git push origin main
git push origin download-1.1.1
```

Consumer install:

```kotlin
maven("https://jitpack.io")
implementation("com.github.kamleshmultitv:mtvdownloader:download-1.1.1")
```
