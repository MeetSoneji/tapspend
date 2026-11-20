# Privacy

**Tapspend collects no data.** No accounts, no analytics, no ads, no crash reporting, no trackers.

The app declares **no `INTERNET` permission**, so it cannot transmit anything. You can verify this yourself in the merged manifest (`apkanalyzer manifest print Tapspend.apk`) or in CI (`scripts/check-permissions.sh`).

Your expenses live in an on-device database. They leave the phone only when *you* export a file (CSV or backup) to a location you pick with the system file picker.

## Permissions

| Permission | Why |
|---|---|
| `RECEIVE_BOOT_COMPLETED` | Re-arm the single midnight alarm that resets the widget's "today" after a reboot. |
| `WAKE_LOCK`, `ACCESS_NETWORK_STATE` | Added by Jetpack WorkManager (used by the Glance widget library). We never use them to read or send data. |

There is no location, contacts, SMS, storage, camera or microphone access.

## Backups

Android cloud backup is disabled (`allowBackup="false"`). Optional exported backups can be password-protected with AES-256-GCM (PBKDF2-HMAC-SHA256, 310,000 iterations). **If you forget the password, the backup cannot be opened.**
