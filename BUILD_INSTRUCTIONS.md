# BT MultiPlay — Build Instructions

## Requirements

- **Android Studio** Ladybug (2024.2) or newer — [download](https://developer.android.com/studio)
- **JDK 17** (bundled with Android Studio)
- **Android SDK** 34 (install via Android Studio SDK Manager)
- Internet connection for first Gradle sync (downloads dependencies ~300 MB)

---

## Building the APK

### Option A — Android Studio (recommended)

1. Open Android Studio
2. Choose **File → Open** and select the `android-app/` folder
3. Wait for Gradle sync to complete (first time ~3–5 min)
4. Connect your **Samsung Galaxy A9** via USB with **USB Debugging** enabled
   - Settings → About tablet → tap Build Number 7 times → Developer options → USB Debugging ON
5. Select your device in the toolbar dropdown
6. Click **▶ Run** (or press Shift+F10) — this builds and installs directly

### Option B — Build APK for manual install

1. In Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. APK is at: `android-app/app/build/outputs/apk/debug/app-debug.apk`
3. Transfer to your Galaxy A9 (USB, Google Drive, email, etc.)
4. On the tablet: Settings → Apps → Special app access → Install unknown apps → enable for your file manager
5. Open the APK file and install

### Option C — Command line (macOS/Linux)

```bash
cd android-app
chmod +x gradlew
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Option C — Command line (Windows)

```cmd
cd android-app
gradlew.bat assembleDebug
```

---

## Samsung Galaxy A9 Setup

### Enable Bluetooth Dual Audio (for simultaneous 2-speaker playback)

1. Settings → Connections → Bluetooth
2. Tap the **⋮ menu** (three dots, top right)
3. Tap **Advanced**
4. Enable **Dual Audio**
5. Connect two Bluetooth speakers
6. Audio plays through both simultaneously

> **Note:** If you don't see Dual Audio, check for a Samsung One UI update.
> Galaxy A9 (2018, SM-A920F) shipped with Android 8, but supports up to Android 10 with One UI 2.0.

### Required Permissions (granted on first launch)

| Permission | Why |
|---|---|
| Bluetooth Scan | Find nearby speakers |
| Bluetooth Connect | Connect and control speakers |
| Location (Android 11 and below) | Required by Android for BT scan |
| Notifications | Persistent connection status |

---

## App Features

| Feature | Details |
|---|---|
| **Scan** | Discovers all nearby BT audio devices; paired devices shown instantly |
| **JBL Detection** | Automatically identifies Boombox 3, Charge 4, Flip, Xtreme, etc. |
| **PartyBoost badge** | Shown on JBL speakers that support PartyBoost sync |
| **Multi-speaker** | Routes audio to all connected speakers (hardware dual audio or software mirror) |
| **Sync test tone** | Plays a tone simultaneously to verify speaker sync |
| **Auto-reconnect** | Saves speakers and reconnects on next boot |
| **Capability updates** | Fetches latest device compatibility data when online |
| **Samsung Dual Audio** | Detects and guides setup of Samsung's native dual BT output |

---

## Multi-Speaker Sync Modes

| Mode | When | Quality |
|---|---|---|
| **Hardware Dual Audio** | Samsung with Dual Audio enabled | Perfect sync, no latency |
| **Software Synchronized** | All other Android devices | Near-sync, ~50–200ms offset possible |
| **JBL PartyBoost** | Two PartyBoost-compatible JBL speakers | Perfect internal JBL sync (pair via JBL Connect+ app) |

> For absolute sync across different brands, use **Samsung Dual Audio** (Galaxy A9 supports this).

---

## Troubleshooting

| Issue | Fix |
|---|---|
| App can't find speakers | Ensure BT is on, tap Scan, check Location permission |
| Speaker connects but no audio | Open system BT settings, set speaker as media output |
| Two speakers, only one plays | Enable Samsung Dual Audio in Settings |
| App crashes on launch | Grant all permissions when prompted |
| Can't install APK | Enable "Install unknown apps" in Settings → Apps |
