# Security policy

Please report vulnerabilities **privately** using GitHub's *Report a vulnerability* (Security → Advisories) on this repository. Don't open a public issue for security problems.

We aim to acknowledge within 7 days. Only the latest release is supported.

## Design notes (for reviewers)

- No `INTERNET` permission; CI fails on any permission outside the allowlist.
- Only `MainActivity` is exported (plus the system-protected QS tile service). `QuickLogActivity` is not exported; all PendingIntents are immutable and explicit; intent extras are validated.
- Import: 10 MB cap, strict schema and range validation, unknown versions rejected, all-or-nothing transaction.
- CSV export neutralises spreadsheet formula injection.
- Release builds strip `android.util.Log` via R8.
- Crypto uses `javax.crypto` only (AES-256-GCM, PBKDF2-HMAC-SHA256).
