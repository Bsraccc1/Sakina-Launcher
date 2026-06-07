# GitHub Actions Setup

This repository builds Android APKs with GitHub Actions.

## What the Workflow Does

- Runs unit tests with `./gradlew testDebugUnitTest`.
- Builds a release APK with `./gradlew assembleRelease`.
- Uploads the APK as a workflow artifact on pushes and pull requests.
- Creates a GitHub Release when a tag starting with `v` is pushed.
- Can also create or update a GitHub Release from manual dispatch.
- Uploads the APK to the release so users can install it directly.

Workflow file:

```text
.github/workflows/build-apk.yml
```

## Create a Release

1. Update version values in `app/build.gradle` if needed:

```gradle
versionCode 106
versionName "v6.5.0"
```

2. Commit and push:

```bash
git add app/build.gradle
git commit -m "chore: bump version to v6.5.0"
git push
```

3. Push a version tag:

```bash
git tag v6.5.0
git push origin v6.5.0
```

GitHub Actions will create a release with:

```text
Sakina-Launcher-v6.5.0.apk
```

## Manual Build and Release

Open the Actions tab and run **Build and Release APK** with `workflow_dispatch`.

Use input `create_release`:

- `true`: build APK and upload it to a GitHub Release using `versionName` as the tag.
- `false`: build APK and keep it as a workflow artifact only.

## Signing Note

The project currently signs release builds with the debug signing config in `app/build.gradle`, so GitHub Actions can produce an installable APK without extra secrets.

For Play Store or long-term production distribution, replace this with a private release keystore and configure GitHub Secrets.
