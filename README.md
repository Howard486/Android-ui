# FoldSpace

**折起來，是手機。展開，就是你的工作空間。**

An Android foldable launcher for the Galaxy Z Fold series, built to the
*FoldSpace Android App Development Spec v0.1*. Kotlin + Jetpack Compose.

This repository contains the **MVP V0.1** implementation (spec §20.1).

---

## Getting the APK

CI builds both a debug and a release APK on every push
(`.github/workflows/android.yml`). Download them from the workflow run's
**Artifacts** section — `foldspace-debug-apk` / `foldspace-release-apk`.

To build locally you need the Android SDK (API 35) and JDK 17:

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # context engine, rule engine, power dock
```

> The release build is signed with the standard debug key so CI can produce an
> installable artifact without secrets. Replace `signingConfig` in
> `app/build.gradle.kts` before shipping anything.

## Installing as your launcher

1. Install the APK.
2. Open FoldSpace once, then **設定 → 預設 Home → 設為預設 Home**
   (`RoleManager.ROLE_HOME`, spec §5.1).
3. Optional, each with its purpose shown before you are sent to Settings:
   - **通知存取** — notification grouping (§10)
   - **使用情形存取** — Smart Dock suggestions (§7)

Every one of these is optional. With none of them granted the launcher still
lists, searches and launches apps; the features that depend on them say so
rather than failing (§21.1).

---

## What V0.1 implements

| Spec | Epic | State |
|---|---|---|
| §5.1 | `ROLE_HOME`, HOME/DEFAULT manifest declaration | ✅ |
| §5.2 | `LauncherApps` inventory, cross-profile launch, package callbacks | ✅ |
| §5.3 | App drawer: A–Z, search, profile tabs, long-press to pin | ✅ |
| §5.4 | Dynamic Dock — fixed pins + usage-driven smart slots | ✅ |
| §5.5 | App/package search | ✅ (settings & AI commands are V1.0) |
| §4 | Folded / Expanded / Tabletop / Book layouts via `WindowInfoTracker` | ✅ |
| §3 | Six Spaces, each with its own dock, cards, policy and theme | ✅ |
| §7 | Context Engine: signals → snapshot → fingerprint cache → rules | ✅ |
| §7.2 | Manual / Suggest-first / Automatic switch modes | ✅ |
| §10 | `NotificationListenerService`, 4-tier rule classification, badges, digest | ✅ |
| §11 | Power Dock — Desk / Bedside / Cyber / Compact Charge | ✅ |
| §6 | Samsung Wallet gesture zone reserved and never claimed | ✅ |
| §15 | Theme Engine — 5 token presets, motion tied to Power Mode | ✅ |
| §12 | Event-driven throughout; no polling loop anywhere | ✅ |
| §16 | Purpose strings, graceful degradation, in-memory-only notification bodies | ✅ |
| §8 | Gemini Nano — boundary defined, capability-detected, **no model wired** | ⏳ V0.5 |
| §13 | `AppWidgetHost` third-party widget hosting | ⏳ V0.5 |
| §14.2 | Private Space container UX | ⏳ V1.0 |

### Deliberate deviations from the spec

- **Persistence is DataStore only, not Room** (§17.1 suggests both). V0.1
  stores settings, the current Space and dock pins — a handful of scalars.
  Room would add a schema, a code generator and build time for data that has
  no queries. Room arrives with the Context Automation Editor (V1.0), which is
  the first feature that actually needs relational data.
- **No DI framework** (§17.1 suggests Hilt or Koin). The graph is one
  Application-scoped container with six objects in it; see
  `FoldSpaceApplication.kt`. Introduce Hilt when there is a second entry point
  worth wiring.
- **Native Cards beyond Clock/Battery/Notifications are placeholders.** They
  render their empty state rather than fake data — Weather and Calendar need a
  data source and a permission flow respectively, and inventing numbers to
  fill a card is worse than an honest blank.

---

## Architecture

```
FoldSpaceApplication (AppContainer — manual DI)
├── SettingsRepository ........ DataStore; the only durable state
├── LauncherAppsRepository .... LauncherApps + package callbacks
├── HomeRoleManager ........... RoleManager.ROLE_HOME
├── ContextEngine ............. events → snapshot → fingerprint → decision
│   ├── ContextReducer ........ pure; unit-tested
│   ├── RuleEngine ............ pure; unit-tested
│   └── NanoAdapter ........... §8 boundary; V0.1 reports unsupported
├── NotificationRepository .... in-memory only, by design (§10.4)
└── LauncherViewModel ......... owns the signal sources for the UI's lifetime
```

### The two rules that shape everything

**§12.1 — no event, no work.** Every signal is a system callback or a
broadcast. `UsageStatsManager` is the one thing actively read, only on
launcher resume, and only every 15 minutes. There is no timer anywhere except
the clock card, which sleeps to the next minute boundary rather than looping.

**§7.3 — the fingerprint decides.** Continuous signals are bucketed before
they reach the fingerprint (battery to 20%, time to five buckets, usage to the
top three package names), so the cache survives a battery tick or a score
drift. `ContextFingerprintTest` pins each of those properties down.

Foreground state is deliberately *not* in the fingerprint — otherwise every
resume would invalidate the cache. `ContextEngine.nanoAttemptedFor` is what
still lets a context evaluated in the background get its one inference when
the launcher comes forward, and only one.

### §8 — why there is no model behind `NanoAdapter`

The spec is explicit (§8.2, §22) that background inference is prohibited, that
a per-app battery quota exists, and that Nano support cannot be assumed beyond
the confirmed device list. So the launcher is built to be complete without it:
`NanoAdapter.Unsupported` is the V0.1 implementation, every caller exercises
its fallback path on day one, and the settings screen reports *why* on-device
AI is unavailable rather than hiding the feature.

### §6 — Samsung Wallet

FoldSpace does not arbitrate with the Wallet gesture; it declines the region.
`GestureZones.WALLET_ZONE_HEIGHT` is reserved as bottom padding that nothing
is laid out into and no pointer handler is installed on. FoldSpace never
simulates or forwards a payment gesture.

> **§22 Release Gate:** the 48dp figure and the gesture hand-off both need
> confirming on real Galaxy Z Fold hardware. One UI versions are not
> guaranteed to agree, and this cannot be validated on an emulator.

---

## Testing

```bash
./gradlew testDebugUnitTest
```

27 unit tests cover the parts §21.2 requires to be provable: fingerprint
stability, the §7.1 decision ladder, that Nano is unreachable in the
background / in battery saver / on an unsupported device, and the §11.2 Power
Dock trigger table.

The Compose layer has no tests yet — that is the first gap to close.

## Still to verify on hardware (§22)

- Galaxy Z Fold Wallet gesture arbitration under One UI.
- Fold / unfold with no layout breakage and no lost UI state.
- Work Profile notification visibility (DPM may block the listener).
- `computeChargeTimeRemaining()` availability — the ETA is hidden where the
  platform does not supply one, rather than estimated.
