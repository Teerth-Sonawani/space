# Changelog

All notable changes to this project will be documented in this file.

## [0.1.0] — 2026-11-24

### Added
- Initial release: idea → questions → flowchart plan generator
- Chart engine with layered top-to-bottom layout, pan/zoom, tap-to-detail
- Provider chain with failover (Gemini, Groq, OpenRouter)
- File-backed plan library with rename and delete-with-undo
- Encrypted API key storage (Android Keystore + DataStore)
- Dark theme with warm neutral surfaces
- Accessibility: TalkBack labels, content descriptions, reduced motion
- Offline starter plan fallback when no keys configured
- 168 JVM tests (failover, JSON decoders, validator, layout, view model, Compose UI via Robolectric, provider HTTP integration)
- Compose UI tests for all 7 screens (Robolectric, no emulator needed)
- Provider integration tests with MockWebServer
- GitHub-ready: README, LICENSE (MIT), CONTRIBUTING, issue templates, screenshots