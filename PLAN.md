# Plan: "Space" (working name), idea → questions → living flowchart

## Status (24-Nov-2026): M1–M6 done
- M1: sample plan + chart engine + validator + layout + staggered reveal,
  pan/zoom, tap-to-detail. Exit test satisfied.
- M2: file-backed library finished — rename and delete with undo via the
  Library screen (`ui.screens.library`), atomic temp-file writes, newest-first
  ordering with a 50-plan cap, saved-plan list surfaced on Home. Exit test:
  save, reopen, rename, delete (+undo) a plan and it is there.
- M3–M5 shipped: `ProviderChain` + `QuestionsChain` run one shared failover core
  (`FailOverRunner.kt`) over the §3 table, `SettingsScreen` is reachable from
  Home (add / remove / reorder keys), the generating screen offers the starter
  plan and cancel, and the questions call falls back to the offline pack.
- M6 shipped: accessibility (labels on Idea/Questions text fields, content
  descriptions on question filter chips, TalkBack labels on chart controls),
  Compose UI tests added for Home and Chart screens (Robolectric, JVM),
  `keystore.properties`/`release.keystore` excluded from git, missing
  `releaseSigningConfig()` build function restored.
- Deviations from this document: persistence is `FilePlanStore` (atomic
  temp-file rename), not Room; settings are EncryptedSharedPreferences +
  DataStore; navigation uses string routes rather than type-safe routes; no DI
  framework (constructor injection only).
- Test suite: 140+ JVM tests, covering the failover table end to end
  (`FailOverRunnerTest`, `ProviderChainTest`, `QuestionsChainTest`), HTTP and
  health mapping, both JSON decoders, and the view-model flow, layout,
  validation, and sample plan. Compose UI tests added for Home, Idea,
  Questions, Generating and Chart screens; they run on the JVM via Robolectric
  — no emulator needed.

## 1. Product in one paragraph
You type a rough idea. The app asks 2–4 sharp questions, one at a time. It then builds a flowchart of the plan, laid out automatically, that you can pan, zoom and tap into. The moment that matters is the chart assembling itself on screen. Everything else exists to get you there quickly and keep your plans safe.

## 2. Stack and architecture
- **Android Studio project**, Kotlin, Jetpack Compose, single Activity, minSdk 26.
- **Material 3 as a base only.** Custom theme, shapes and type, so it doesn't look like stock M3.
- **MVVM with unidirectional state.** Each screen has one `UiState` sealed type (Loading / Content / Error), so loading, error and empty states are designed rather than bolted on.
- **Libraries:** Navigation Compose with type-safe routes, Room (plans), DataStore (settings), OkHttp with kotlinx.serialization (networking), Hilt or plain constructor DI. Pick one, don't mix.
  - *As built:* string routes, `FilePlanStore` instead of Room, EncryptedSharedPreferences + DataStore for settings, constructor injection (no DI framework).
- **Layers:** `ui` → `domain` (models, `PlanGenerator`, validation, layout) → `data` (Room, providers, key store). Domain is pure Kotlin, so it is easy to unit test.

## 3. AI layer
**Two LLM calls, not one.**
1. **Questions call:** idea in, 2–4 questions out, each with 2–3 tappable suggested answers plus free text.
2. **Plan call:** idea plus answers in, strict JSON plan out.

**Provider chain**
- `LlmProvider` is a small interface (`complete(request): Result`). Implement one adapter per vendor. Start with 2–3 free-tier providers (Gemini, Groq and OpenRouter free models are the usual candidates; check current free-tier terms before building, since they change often).
- `ProviderChain` tries the user's keys in priority order, then the fallback tier.

**Failover rules**

| Failure | Action |
|---|---|
| 429 / quota | Cool this key down (honor `Retry-After`, else a default window) and try the next |
| 5xx, timeout, no network | Try the next key. Retry the same key once only for a transient 5xx |
| 401 / 403 | Mark the key invalid, surface it in Settings, try the next |
| 400 or bad request | Don't fail over. This is a bug in our prompt, so log it |
| Malformed JSON | One "repair" retry on the same provider, then try the next |

- Each attempt has a hard timeout (about 20s for the plan call). The whole chain has an overall budget, so a user never waits through six dead keys.
- Cooldown state lives in memory with a short TTL, so a dead key isn't hammered on every request.
- Settings shows per-key health: Active, Cooling down, Invalid.

**Fallback tier**
- Free keyless endpoints are unreliable and send the user's idea to a third party. Treat the fallback as a pluggable provider with a one-time consent line ("Your idea will be sent to X").
- **Do not ship a developer API key inside the APK.** If a built-in free path is wanted later, put a thin rate-limited proxy in front of it (v2).
- The last resort is the deterministic template generator. It produces a generic but valid plan (Goal → Research → Plan → Build → Review) so the app never dead-ends. Label it clearly as "offline starter plan".

**Plan schema (validated before anything renders)**
- Nodes: `id`, `title` (≤ 6 words), `summary` (1 line), `detail` (markdown), `kind` (goal / step / decision / risk), and optional `subtasks`.
- Edges: `from`, `to`, optional `label`.
- Hard checks: unique ids, edges reference real nodes, graph is acyclic, 6–16 nodes, one start node. If a check fails, that counts as malformed JSON, so it goes through the repair-then-failover path above.
- The LLM never decides positions. Layout is computed on-device.

## 4. Chart engine (build this first, it's the riskiest part)
- **Layout:** layered top-to-bottom layout. Assign each node a rank by longest path from the start, order nodes within each rank to reduce edge crossings, then space them evenly. Simple and good enough for ≤16 nodes.
- **Rendering:** nodes are real composables (accessibility, ripples, easy animation). Edges drawn on a `Canvas` as smooth Béziers.
- **Interaction:** pan and zoom via `transformable`, fit-to-screen on load, tap to select, re-center button.
- **Performance:** cap at 16 nodes in v1. Hoist layout out of composition (compute once in the ViewModel).

## 5. Screens and states
1. **Home / Library:** saved plans, plus a prominent "new idea" entry. Empty state is designed.
2. **Idea:** one large text field, nothing else competing with it.
3. **Questions:** one question per screen with suggested-answer chips, free text, skip, progress indicator.
4. **Generating:** calm designed wait state showing which step is running ("Thinking through your answers…"). Cancelable.
5. **Chart:** the plan itself.
6. **Node detail:** bottom sheet with summary, detail text and subtasks.
7. **Settings → API sources:** add, reorder (drag) and remove keys, see health, toggle the public fallback.

Error paths: bad key, all sources failed (offers "use offline starter plan" or retry), offline, cancelled generation.

## 6. Motion spec
- **Chart reveal:** nodes fade and rise in, staggered by rank (about 40–60 ms per rank). Edges draw on after their source node lands, using `PathMeasure`.
- **Node → detail:** tapped node lifts slightly; sheet settles with a low-bounce spring. Rest of the chart dims a little.
- **Screen transitions:** short shared-axis fades and slides (about 250–300 ms). No slow flourishes.
- **Question → question:** old card slides out as the next slides in.
- Respect the system "remove animations" setting. Motion clarifies state, doesn't decorate.

## 7. Design: avoiding the "AI-generated" look
- **One accent color** against a warm neutral surface, proper dark theme. No purple-blue gradients, no sparkle icons, no glassmorphism, no emoji bullets.
- **Type:** one typeface family, small scale (about 5 sizes), generous line height.
- **Spacing:** 8dp grid applied consistently.
- **Elevation:** tone and thin outlines instead of heavy shadows. Node shape/color encode kind (goal / step / decision / risk), nothing else gets styled.
- **Copy:** short, specific, plain. No "Unleash your creativity" language.
- Define tokens (color, type, spacing, motion durations) in one place before building any screen.

## 8. Persistence
- **Plans:** `FilePlanStore` — one JSON file per plan, atomic temp-file rename,
  newest-first with a 50-plan cap (in place of Room; `InMemoryPlanStore` for tests).
- **Drafts:** in-progress idea and answers autosaved (`saveDraftNow`), restored
  on launch only when the idea is blank.
- **Library:** rename and delete plans (delete with undo).
- **API keys:** encrypted with Android Keystore-backed key (Tink, or Keystore AES directly), stored in DataStore. Never log keys, exclude from auto-backup, mask in UI.

## 9. Milestones, each with an exit test
| # | Milestone | Done when |
|---|---|---|
| M0 | Project skeleton, design tokens, theme, navigation | Empty screens navigate with the final transitions |
| M1 | Plan model, validator, layout, chart screen on a **hardcoded sample plan** | Staggered reveal, pan/zoom and tap-to-detail feel good on a mid-range device |
| M2 | Room persistence and Library | Save, kill app, reopen, and the plan is there |
| M3 | One real provider plus plan call plus schema validation | A real idea produces a valid chart |
| M4 | Questions call and Q&A flow, with draft autosave | Full flow works end to end on one key |
| M5 | Provider chain, cooldowns, Settings, fallback tier, offline starter plan | With the first key revoked, the second takes over. With all keys dead, you get the fallback or starter plan |
| M6 | Polish: accessibility, reduced motion, error states, dark theme, perf pass | ✅ TalkBack labels on chart controls, text-field labels on Idea/Questions, question chip content descriptions, Compose UI tests (Home, Chart) via Robolectric on the JVM |

## 10. Testing
- **Unit tests:** failover logic with fake providers (every row of the failure table), schema validation (cyclic graphs, dangling edges, too many nodes), layout determinism. ✅ done — `FailOverRunnerTest`, `ProviderChainTest`, `QuestionsChainTest`, `HttpFailOverTest`, `KeyHealthStoreTest`, decoder and layout tests, 140 JVM tests green.
- **Build gates:** ✅ `assembleDebug`, `assembleRelease` (R8 minified), and `lintDebug` all green; lint reports 0 errors (remaining warnings are dependency-version notices and a portrait-orientation choice).
- **Compose UI tests:** ✅ written for Home, Idea, Questions, Generating and Chart screens; run on the JVM via Robolectric — no emulator needed.
- **Manual checks:** airplane mode, revoked key, slow network. ⬜ not run.

## 11. Risks
| Risk | Mitigation |
|---|---|
| Free tiers change or vanish | Providers are adapters; none hard-wired. Starter plan guarantees a result |
| LLM returns weak or odd graphs | Strict schema, repair retry, node cap, prompt tuned against ~10 test ideas |
| Layout looks messy on branching plans | Crossing-reduction pass, node cap, test on branching-heavy samples early (M1) |
| User ideas sent to third parties | Consent line for fallback, clear note in Settings, keys stored locally only |
| "Not AI-looking" stays subjective | Design rules in section 7 act as a review checklist at M6 |

## 12. Out of scope for v1
Accounts and sync, backend or proxy, freeform editing/dragging of nodes, streaming nodes in as they generate, export to image or markdown. Streaming and export are the best v1.1 candidates.

## Defaults assumed (change any of these)
- minSdk 26, portrait-first.
- Gemini, Groq and OpenRouter as the first provider adapters.
- Top-to-bottom layout.
- 16-node cap.
