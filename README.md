# Expense Tracker

An Android app that reads your bank SMS alerts and turns them into a monthly view of where your money goes. Tuned for SBI (UPI, account, ATM and SBI Card alerts) and also handles HDFC, ICICI, Axis, Kotak and other Indian banks.

- **Past messages**: on first launch you choose how far back to read (last month to everything). Whole months are read, so "Last month" covers all of it.
- **New messages**: a bank SMS is picked up a few seconds after it arrives.
- **Categories**: merchants are matched to Food, Groceries, Shopping, Transport, Fuel, Bills, Rent, Entertainment, Health, Travel, Education, Subscriptions, Transfers or Cash / ATM. Anything unmatched goes to **Others** (or **Other Income** for money in).
- **Learns from you**: re-file a transaction, tick "Use for all payments to …", and that merchant is sorted that way from then on.
- **Credits are tracked too**: salary, refunds, interest and money from people.
- **No double counting**: credit card bill payments (including CRED) and transfers between your own accounts are shown but left out of totals.
- **Private**: everything stays on the phone. The app has no internet permission.

## Install on your phone

1. On the phone, turn on **Developer options**: Settings → About phone → tap **Build number** 7 times.
2. In Developer options, turn on **USB debugging**.
3. Connect the phone to the Mac with a USB cable and tap **Allow** on the phone's prompt.
4. From this folder, run:

   ```bash
   ./install-on-phone.sh
   ```

5. In the app, pick how far back to read, tap **Allow SMS access**, and allow the permission.

If something gets in the way:

- **Xiaomi / Redmi / POCO**: also turn on **Install via USB** in Developer options. If no transactions appear, open the app's permissions and allow SMS, including any "Notification SMS" toggle.
- **Installed by copying the APK instead of USB**: newer Android versions may block the SMS permission as a "restricted setting". Open App info → ⋮ → **Allow restricted settings**, then grant SMS.
- **Play Protect warning**: this is expected for a personal app that isn't from the Play Store. Choose **Install anyway**.

## Develop

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew testDebugUnitTest   # parser and categorizer tests
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```

Stack: Kotlin, Jetpack Compose + Material 3, Room, WorkManager, DataStore, Hilt, Navigation Compose, and coroutines + Flow. Charts are drawn with Compose Canvas.

| Where | What |
|---|---|
| `sms/SmsParser.kt` | Turns an SMS into a transaction: amount, debit or credit, merchant, account, reference. Ignores OTPs, offers, reminders and failed payments. |
| `sms/Categorizer.kt` | Picks the category: learned merchants, then keywords, then Others. |
| `data/db/DefaultCategories.kt` | Built-in categories and their keywords. |
| `sms/SmsImporter.kt`, `SmsSyncWorker.kt`, `SmsReceiver.kt` | Reads the inbox in the background and reacts to new SMS. |
| `ui/` | Overview, Transactions, Categories and Settings screens. |

When a bank message isn't recognised, add its text as a case in `SmsParserTest.kt` and adjust the parser until it passes.
