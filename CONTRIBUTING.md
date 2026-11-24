# Contributing to Space

Thanks for your interest in contributing. This project is a single-developer
Android app, but issues and pull requests are welcome.

## Getting started

1. Fork the repo and clone your fork
2. Open in Android Studio (latest stable) or set up the CLI
3. Run `./gradlew testDebugUnitTest` — all 168 tests should pass
4. Run `./gradlew assembleDebug` — should build cleanly
5. Run `./gradlew lintDebug` — should report 0 errors

## Before you submit

- [ ] `./gradlew testDebugUnitTest` passes
- [ ] `./gradlew assembleRelease` builds (R8 minification)
- [ ] `./gradlew lintDebug` reports 0 errors
- [ ] New code has tests (unit tests in `app/src/test`, Compose UI tests via Robolectric)
- [ ] No secrets, API keys, or keystore files are committed

## Code style

- **Architecture:** `ui → domain → data`, constructor injection only, no DI framework
- **Domain layer** is pure Kotlin (no Android imports) — keep it that way
- **Compose:** one `UiState` sealed type per screen, no business logic in composables
- **Tests:** JUnit 4 + Robolectric for UI, MockWebServer for HTTP, fakes for providers
- **Naming:** PascalCase for classes, camelCase for functions/properties, UPPER_SNAKE for constants

## Reporting issues

Open a GitHub issue with:

- What you did
- What happened
- What you expected
- Device / Android version / API key provider (if relevant)

## Pull requests

1. Create a branch from `master` (`fix/...`, `feat/...`, `chore/...`)
2. Keep commits focused — one logical change per commit
3. Write a clear PR description explaining the what and why
4. Link any related issues