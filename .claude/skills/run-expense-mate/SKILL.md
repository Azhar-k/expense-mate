---
name: run-expense-mate
description: Build, install, run and drive the expense-mate Android app on an emulator. Use when asked to run or start the app, try a change on the emulator, take a screenshot, tap through a screen, send a test SMS, check for crashes, or run the unit tests.
---

expense-mate is a single-module Android app (`:app`). An agent drives it on the `Pixel_7_Pro` emulator with
`.claude/skills/run-expense-mate/driver.sh`, a Git Bash wrapper around adb: it boots the emulator, builds and
installs the debug APK, then reads the screen as a list of UI nodes, taps by label or id, types, takes screenshots,
sends SMS into the app's capture path, and backs up and restores the app's data. All paths are relative to the repo
root. Written and verified on Windows 11 with Git Bash.

## Prerequisites

- Android SDK at the `sdk.dir` in `local.properties` (here `E:\Dev\Android\Sdk`), with `emulator` and `platform-tools`.
- An AVD named `Pixel_7_Pro` (`"$SDK/emulator/emulator.exe" -list-avds`). Another AVD works with `AVD=<name>`; the
  screen coordinates in Gotchas assume this one (1440x3120).
- A JDK. Nothing is on `PATH`; the driver sets `JAVA_HOME` to the newest `~/.jdks/jbr-*` when it is unset.

## Run (agent path)

```bash
D=.claude/skills/run-expense-mate/driver.sh
bash $D boot      # starts the emulator if none is attached, waits for boot ("booted")
bash $D build     # ./gradlew :app:assembleDebug -q, then lists the APK
bash $D install   # adb install -r, grants READ_SMS/RECEIVE_SMS
bash $D launch    # clears logcat, cold-starts MainActivity on Home
```

Look at the app:

```bash
bash $D shot home                  # app/build/run-shots/home.png - open it with Read and look
bash $D dump                       # clickable or labelled nodes: text|desc|id|clickable|bounds
bash $D find tvSpent               # nodes whose text, desc or id contains the string
bash $D errors                     # crashes and "Error ..." write failures since launch
```

Act on it (labels and ids come from `dump`; ids drop the `com.example.expensemate:id/` prefix):

```bash
bash $D id navigation_activity     # bottom tabs: navigation_home/_activity/_bills/_accounts
bash $D id fabAdd                  # Add on Home/Activity opens the transaction sheet
bash $D id etAmount && bash $D type 9 && bash $D text Food
bash $D key 111                    # 111 = Escape, hides the keyboard
bash $D back                       # closes the sheet without saving
bash $D id navigation_accounts && sleep 2
bash $D hold Daily                 # long press: Edit / Delete (plus Set as default on non-default accounts)
bash $D back
bash $D drawer                     # hamburger: Money lent, Categories, Scan messages, Settings
bash $D back
bash $D scroll down
```

`text` and `id` take an optional second argument, N, for the Nth match: `bash $D text Daily 2`.

Send a bank SMS through the real receiver → `CaptureService` path, and leave no trace:

```bash
bash $D backup                     # app/build/run-backup/app-data.tar (force-stops the app)
bash $D sms AXICICIT "ICICI Bank Acct XX444 debited for Rs 42.00 on 05-Nov-26; SKILLCHECK credited."
sleep 5
bash $D launch && bash $D id navigation_activity && sleep 2 && bash $D find SKILLCHECK
bash $D smsdel SKILLCHECK          # remove it from the inbox ("No result found." = gone)
bash $D restore && bash $D launch  # data exactly as before
```

The receiver parses that ICICI format; the text before ` credited.` becomes the payee. Sending the same body twice
saves one row (logcat: `Duplicate transaction detected`).

## Run (human path)

Open the project in Android Studio and press Run with the `Pixel_7_Pro` device selected.

## Test

```bash
./gradlew :app:testDebugUnitTest -q   # needs JAVA_HOME, see Troubleshooting; ~2 min, 128 Robolectric tests
```

## Gotchas

- **The emulator holds real-looking data.** Run `backup` before anything that writes and `restore` after. Restoring
  is not enough after an SMS test: on launch the app's catch-up imports inbox SMS newer than the restored
  `SmsMonitorPrefs`, so `smsdel` the test messages first or they come straight back.
- **`backup` force-stops the app**, so the screen leaves it. `launch` again before reading the UI.
- **`boot` runs with `-no-snapshot-save`**, so the next cold boot resumes the AVD's older quickboot snapshot: installs
  and data changes from the last session are gone, and the installed app may be the differently signed copy again.
  That is why `install` handles the signature mismatch itself.
- **An emulator started from a background tool task dies when that task ends.** `boot` detaches it, so it survives
  between commands.
- **Quickboot resumes the old screen** (Money lent, a sheet, ...). `launch` uses `am start -S` so every run starts on
  Home; a plain `am start` only prints `Activity not started, intent has been delivered`.
- **`type` drops everything after the first space** (`adb input text "TEST SHOP"` gives `T`). Use one-word values.
- **`dump` takes about 3 s.** The "Transaction deleted / Undo" snackbar is gone by then: tap Undo straight after
  Delete by coordinates (`bash $D tap 1283 2644` on this AVD). The restored row also takes a moment to appear.
- **Coordinates go stale after any navigation.** `back` resets list scroll; reusing old coordinates long-pressed the
  wrong account and made it the default. Re-`find` before tapping.
- **Horizontal chip rows** (categories, accounts in the sheets) only show visible chips in `dump`. Swipe the row
  itself, e.g. `bash $D swipe 1300 1595 100 1595`, using the y from `dump`. Never swipe right from the screen's left
  edge: that is the system back/feed gesture and leaves the app.
- **Two `back`s from a sheet exit the app** (the first closes the sheet).
- **The Bills tab's Add creates a bill**, not a transaction; add transactions from Home or Activity.
- **The default account's long-press menu has no "Set as default"**; only Edit and Delete.
- **A row that needs review shows no checked category chip.** Its current category is the first chip in the details
  sheet; the check only appears once a category is chosen.
- **No `sqlite3`** on the device or in Git Bash; verify through the UI (Home totals, Activity pills, details sheet).

## Troubleshooting

| Symptom | Fix |
|---|---|
| `ERROR: JAVA_HOME is not set and no 'java' command could be found` | Use the driver, or `export JAVA_HOME=$(ls -d ~/.jdks/jbr-* \| tail -1)`. Piping a `-q` build through `grep` hides this and looks like success. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE: ... signatures do not match` | The installed copy came from another debug key. `install` handles it: backs up via `run-as`, uninstalls, installs, restores. |
| `cat: C:/Program Files/Git/sdcard/ui.xml: No such file or directory` | Git Bash path conversion; the driver sets `MSYS_NO_PATHCONV=1`. Set it yourself for raw adb calls. |
| `error: no devices/emulators found` | The emulator exited; `bash $D boot`. |
| `NOT FOUND: <label>` | Not on screen yet or scrolled away: `sleep 2`, `scroll`, or `dump` to see what is there. |
