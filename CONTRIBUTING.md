# Contributing

Thanks for helping! Keep these rules; they protect the app's two promises (private, battery-friendly).

1. **No new permissions, no `INTERNET`, no analytics/ads SDKs.** CI runs `scripts/check-permissions.sh`.
2. **No background work**: no services, WorkManager periodic jobs, exact/wakeup alarms, polling loops.
3. **Money is a `Long` in minor units + ISO-4217 code.** Never `Double`/`Float`. Aggregate in SQL.
4. **Database changes** bump the Room version, commit the new `app/schemas/*.json`, and ship a migration. Never `fallbackToDestructiveMigration`.
5. **Strings go in `strings.xml`** (plain, friendly English; no hard-coded UI text).
6. Run `./gradlew :app:testDebugUnitTest :app:lintDebug` before opening a PR; add tests for logic you touch.

Use `docs/adr/` to record decisions that future contributors would otherwise have to rediscover.
