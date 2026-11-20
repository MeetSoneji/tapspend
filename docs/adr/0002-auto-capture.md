# ADR 0002: Auto-capture from bank alerts, reviewed by the user

**Status:** accepted · **Date:** 2026-11-20

**Problem.** People forget to log spends. Most spending is on credit cards and UPI, where the card bill is paid later as one lump sum that should not count as spending a second time.

**Decision.**
- Sources: bank/card **SMS** (RBI-mandated alerts, complete, works offline, allows a 90-day import) plus **payment-app notifications** for payments without an SMS. Email is excluded because it would need internet access.
- Everything is parsed on-device by `TxnParser` (pure Kotlin, unit-tested) and judged by `Classifier`: expense, refund, cashback, transfer (card bill, self, wallet, investment, ATM) or income.
- Every detected item starts as **PENDING** and needs one tap. The user chose "always ask"; unanswered items wait in the inbox and a single daily reminder (inexact `RTC` alarm, user-chosen time) nudges them.
- Duplicates (SMS + app alert, or an alert for something already logged by hand) are merged using amount, a 10-minute window, the reference number and the card or account's last 4 digits.
- Self-transfers: a debit and a credit of the same amount in two different own accounts within 15 minutes, payee equals the user's full name, or known patterns (CRED/card bill, wallet top-up, brokers).
- Learned rules (`merchant_rule`) store the user's last decision per merchant and override built-in guesses.
- Totals count only CONFIRMED items: expenses minus refunds and cashback, in the month they post.
- Distribution stays GitHub/F-Droid-style: Google Play does not allow SMS access for this kind of app.

**Not yet:** per-card statement reconciliation (statement SMS are already recognised) and account aggregator (needs a server and a licensed partner).
