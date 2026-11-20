# ADR 0001: Pragmatic deviations from the original build plan

**Status:** accepted · **Date:** 2026-11-20

The original plan proposed ~15 Gradle modules, Hilt, Proto DataStore, Paging 3 and an ICU `NumberFormatter`. For a v1 with one developer and one verifiable APK we chose a simpler shape and kept the plan's *guarantees* (no INTERNET, no background work, SQL aggregates, Long money).

| Plan | What we did | Why |
|---|---|---|
| Multi-module + convention plugins | Single `:app` module, packages `core/ data/ ui/ widget/ tile/` | Faster builds, less config to maintain; `core/` is pure Kotlin and JVM-tested. Split later if the codebase grows. |
| Hilt | Hand-rolled `AppContainer` (lazy) | No codegen, smaller APK, faster cold start. |
| Proto DataStore | Preferences DataStore | No protobuf plugin; the settings are flat. |
| Paging 3 | SQL-filtered `Flow` + `LazyColumn` | Per-month lists are small; verified smooth with ~85 rows. Revisit at 10k+/month. |
| ICU NumberFormatter | Pure-Kotlin `Money` formatter (Indian grouping for INR) | JVM-testable; ICU needs API 30+. |
| SVG icon subset | Emoji category icons | Zero asset bytes, instantly personal. |
| Drag-to-reorder categories | ▲/▼ buttons | Simpler and more accessible. |
| Baseline profiles / Macrobenchmark | Not yet | Planned follow-up; APK is already ~3 MB. |
| `foss`/`play` flavors | `foss` only | No Play Store target for v1. |

Battery note: WorkManager (pulled in by Glance) registers one library-internal housekeeping job that only runs while charging; the app itself schedules no jobs, services or wake locks, and uses exactly one non-wakeup `RTC` alarm for the midnight widget reset.
