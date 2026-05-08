# Haulio Monorepo

Unified monorepo for Haulio mobile, shared Kotlin Multiplatform, backend, web dashboard, and infrastructure code.

## Repository Structure

- `ios/` - iOS app and Apple platform assets.
- `android/` - Android app (`android/app` Gradle module).
- `shared/` - Kotlin Multiplatform shared module.
- `backend/` - Backend services and APIs.
- `web-dashboard/` - Web admin/dashboard frontend.
- `infrastructure/` - IaC and deployment configuration.

## Gradle Monorepo Setup

Root Gradle configuration includes:
- `:android:app` -> mapped to `android/app`
- `:shared` -> mapped to `shared`

## Build Instructions

### Windows (KMM shared module)

Run from repo root:

```bat
gradlew.bat :shared:build
```

### Android app

```bat
gradlew.bat :android:app:assembleDebug
```

### Unix/macOS

```sh
./gradlew :shared:build
./gradlew :android:app:assembleDebug
```

## Notes

- Ensure JDK 17+ is installed and `JAVA_HOME` is configured.
- Gradle wrapper uses Gradle `8.5`.
- Android Gradle Plugin is pinned at `8.2.0` and Kotlin at `2.0.0` in root build config.