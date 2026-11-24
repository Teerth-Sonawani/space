# Space

Type a rough idea → answer 2–4 sharp questions → watch a flowchart plan assemble
itself on screen. Android app, Jetpack Compose, single Activity.

## The flow

```
Home → Idea → Questions → Generating → Chart (pan / zoom / tap a node for detail)
                       ↘ Library (saved plans)   ↘ Settings (API keys)
```

Every step has a designed loading, error and empty state. If generation fails
outright, you can fall back to a built-in starter plan and still get a chart.

## AI layer

Two LLM calls share one failover implementation (`domain/generation/FailOverRunner.kt`):

1. **Questions call** — idea in, 2–4 clarifying questions out.
2. **Plan call** — idea plus answers in, strict JSON plan out.

Failover table (PLAN.md §3), applied identically to both:

| Condition | Behaviour |
|---|---|
| No keys configured | questions → offline pack, plan → starter plan |
| 401 / 403 | key marked **Invalid**, try next source |
| 429 | key **Cooling** for `Retry-After` (default 60s), try next source |
| 5xx | one retry on the same source, then try next |
| Network / timeout / malformed | short soft-cool (15s), try next |
| 400 | stop — that is a prompt bug, not a key problem |
| All sources cooling/invalid or budget spent | report the reason; offer starter plan |

Malformed model JSON gets one repair retry before failing over.
Keys are tried in Settings priority order; the chain skips any key that is
cooling or invalid rather than hammering it.

## Architecture

`ui → domain → data`, no DI framework, constructor injection only.

- `domain/` — pure Kotlin: models, prompts, JSON decoding, `PlanValidator`,
  layout, and the failover core. Everything here is JVM-unit-testable.
- `data/` — `FilePlanStore` (atomic temp-file rename, 50-plan cap),
  `EncryptedSettingsRepository` (EncryptedSharedPreferences + DataStore),
  in-memory variants for tests.
- `ui/` — Compose screens plus one `PlanFlowViewModel` holding the whole flow,
  with injectable seams (`store`, `generator`, `questionsChain`,
  `ioDispatcher`, `settings`) for tests.

Providers (Gemini, Groq, OpenRouter) are adapters behind `LlmClient`, so none is
hard-wired.

## Build and test

Requires an Android SDK (platform 36) and JDK 25+.

```powershell
# unit tests (domain + view model, no emulator needed)
.\gradlew.bat testDebugUnitTest --console=plain

# build
.\gradlew.bat assembleDebug --console=plain
```

If `JAVA_HOME` in your environment points at an invalid JDK, override it for
the session: `$env:JAVA_HOME='C:\Program Files\Java\jdk-26.0.2.1'`.

## Tests

`app/src/test` — JVM tests only (no emulator needed):

- `FailOverRunnerTest`, `ProviderChainTest`, `QuestionsChainTest`,
  `HttpFailOverTest`, `KeyHealthStoreTest` — the failover table end to end.
- `PlanJsonDecoderTest`, `QuestionsJsonDecoderTest`, `JsonExtractTest` — model
  output parsing, including fenced/prose-wrapped JSON.
- `PlanValidatorTest`, `LayeredLayoutTest`, `PlanCodecTest`,
  `FilePlanStoreTest`, `SamplePlanTest`, `StarterPlanGeneratorTest`.
- `PlanFlowViewModelTest` — flow state, drafts, library, questions fallback,
  source reordering.
- `HomeScreenUiTest`, `IdeaScreenUiTest`, `QuestionsScreenUiTest`,
  `GeneratingScreenUiTest`, `ChartScreenUiTest` — Compose UI tests on the JVM
  via Robolectric; no emulator needed.

Not covered: `androidTest` instrumentation tests — they need an emulator.

See `PLAN.md` for the full design and milestone status.
