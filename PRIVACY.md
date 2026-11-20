# Privacy

**Tapspend collects no data.** No accounts, no analytics, no ads, no crash reporting, no trackers.

The app declares **no `INTERNET` permission**, so it cannot transmit anything. You can verify this in the merged manifest (`apkanalyzer manifest print Tapspend.apk`) or in CI (`scripts/check-permissions.sh`).

## What Tapspend reads, and what it keeps

When you turn on auto-capture, Tapspend reads bank and card alert SMS and the notifications of a fixed list of payment and bank apps (GPay, PhonePe, Paytm, BHIM, CRED, Amazon Pay and major bank apps). It reads only messages from bank sender IDs such as `VM-HDFCBK`; messages from personal numbers are skipped.

Each alert is parsed on the phone. Tapspend keeps only: amount, merchant name, card or account (last 4 digits), time, and the bank's reference number (used to avoid counting a payment twice). **The message text itself is never stored**, and nothing leaves the phone.

## Permissions

| Permission | Why |
|---|---|
| `RECEIVE_SMS` | Read new bank and card alerts as they arrive. |
| `READ_SMS` | One-time import of the last 90 days of bank alerts, when you choose it. |
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Read payment confirmations from UPI and bank apps, for payments with no SMS. You grant it in system settings and can revoke it any time. |
| `POST_NOTIFICATIONS` | Ask you about each payment, and the daily review reminder. |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the daily reminder and the widget's midnight reset after a restart. |
| `WAKE_LOCK`, `ACCESS_NETWORK_STATE` | Added by Jetpack WorkManager (used by the widget library). Never used to read or send data. |

There is no location, contacts, storage, camera or microphone access.

## Backups

Android cloud backup is disabled. Backups you export can be password-protected with AES-256-GCM (PBKDF2-HMAC-SHA256, 310,000 iterations). **If you forget the password, the backup cannot be opened.**
