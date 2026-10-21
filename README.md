<div align="center">

<img width="100%" alt="header" src="https://capsule-render.vercel.app/api?type=waving&height=210&text=YWhatsapp%20Key&fontAlign=50&fontAlignY=36&fontSize=56&desc=Android%20Key%20Scanner%20%7C%20Accessibility%20%7C%20Vision%20%7C%20Floating%20Overlay&descAlign=50&descAlignY=58"/>

<img alt="typing" src="https://readme-typing-svg.demolab.com?font=Inter&size=18&duration=3000&pause=650&center=true&vCenter=true&width=900&lines=Auto+Read+4-Digit+Key+%7C+Accessibility+Node+%2B+Screen+Vision;Auto+Tap+Generate+Until+Key+Matches+Target;Auto+Save+%7C+Tap+Save+on+Match+or+Stop+and+Show;Floating+Panel+%7C+Draggable+%7C+Collapsible+Bubble;Twin+Mode+%7C+Repeated+Keys+%7C+Multi-Target+List"/>

<p>
  <img alt="kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white"/>
  <img alt="minSdk" src="https://img.shields.io/badge/Min%20SDK-30%20(Android%2011)-111111"/>
  <img alt="targetSdk" src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-111111"/>
  <img alt="author" src="https://img.shields.io/badge/by-Yuurisandesu-111111"/>
</p>

<p>
  <b>YWhatsapp Key</b> is an Android app that reads a four-digit key from the screen of another app,
  taps the generate button until the key matches a configured target, and either taps save automatically
  or stops and shows the key so the user saves it manually, controlled by a single toggle.<br/>
  It uses an accessibility service and a built-in screen vision engine. No root, no ADB connection required.<br/>
  Built and distributed by <b>Yuurisandesu</b>.
</p>

</div>

---

## Table of Contents

- [Versions](#versions)
- [Requirements](#requirements)
- [Installation](#installation)
- [Setup](#setup)
- [Usage](#usage)
- [Target Handling](#target-handling)
- [Calibrating Buttons](#calibrating-buttons)
- [Stopping the Engine](#stopping-the-engine)
- [How It Works](#how-it-works)
- [Features](#features)
- [Permissions](#permissions)
- [File Structure](#file-structure)
- [Disclaimer](#disclaimer)

---

## Versions

Two versions of this tool are available depending on how you prefer to run it:

| Version | Repo | Method | Root Required |
|---|---|---|---|
| YWhatsapp Key (this repo) | https://github.com/Yuurisan-N1/YWhatsapp-Key | Accessibility service, no ADB | No |
| Whatsapp Key (ADB version) | https://github.com/Yuurisan-N1/Whatsapp-Key | ADB command over USB or wireless | No |

The ADB version runs entirely from a connected computer and does not need to be installed on the device as an app. The accessibility version runs as a standalone Android app directly on the device.

---

## Requirements

- Android `11+` (API level 30)
- JDK 17 (to build from source)
- Android SDK with platform 35 and build-tools 35.0.0

---

## Installation

### Build from Source

**Clone the repository:**

```bash
git clone https://github.com/Yuurisan-N1/YWhatsapp-Key.git
cd YWhatsapp-Key
```

**Create `local.properties` with your SDK path:**

```
sdk.dir=/path/to/Android/Sdk
```

**Build the release APK:**

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Or use the package script which builds and produces the source archive:

```bash
./package.sh
```

### Signing (optional for release)

Generate a keystore before building a signed release:

```bash
mkdir -p app/keystore
keytool -genkeypair -v \
  -keystore app/keystore/ywk.jks \
  -alias ywk \
  -keyalg RSA -keysize 2048 -validity 12000 \
  -dname "CN=Yuurisan, OU=YWhatsappKey, O=Yuurisan, L=Jakarta, C=ID"
```

Set these environment variables before a signed build:

| Variable | Purpose |
|---|---|
| `YWK_STORE_PASS` | Keystore password |
| `YWK_KEY_PASS` | Key password |

Without a keystore the build still succeeds and produces an unsigned APK, which is enough to test that the code compiles.

**Transfer the APK to your Android device**, enable **Install from unknown sources** in your device settings, and open the file to install.

---

## Setup

Open the app after installing. Three permissions are required before the service can start:

1. **Overlay** tap the Overlay row and allow the app to draw over other apps
2. **Accessibility** tap the Accessibility row, find YWhatsapp Key in the list, and enable it
3. **Battery optimisation** tap the Battery row and allow the app to ignore battery optimisation so the panel does not disappear in the background

All three rows show a green pill when the permission is granted. Once all three are green, tap **Start** to launch the floating panel.

---

## Usage

1. Open the screen in your target app that shows the four-digit key
2. Tap Start in YWhatsapp Key. A floating bubble and a panel appear on screen
3. Drag the panel by its title bar to position it. Tap the arrow next to the close button to collapse it to the title bar only. The position is remembered across sessions
4. Type one or more target keys in the target field, or leave it empty
5. Toggle **Auto save** to decide what happens when the key matches:
   - On: the engine confirms the read and taps the save button in the target app automatically
   - Off: the engine stops generating when the key matches and leaves it on screen. The save button is pressed by hand
6. Tap **Start** in the panel to begin the engine

The large number in the panel always shows the last key actually read from the target app. The label below it shows the source: `teks` when the key came from the accessibility tree, `layar` when it came from the screen scanner. Dashes are shown when nothing can be read. While the engine is idle the key refreshes every 2.5 seconds, so tapping generate manually in the target app should change the number in the panel.

---

## Target Handling

The target field accepts a list of keys, not just one. Non-digit characters are stripped as you type and the remaining digits are grouped into sets of four. The formats `0986 6036`, `0986,6036`, and `09866036` all produce the same two targets. Groups shorter than four digits are dropped. Duplicates collapse. Up to 16 targets are stored at once. The label above the field changes to `Target 3` when more than one is stored. Clearing the field removes all targets.

If the target field is empty, the engine stops at the first key it reads with enough confidence and shows it. It does not wait for a specific value.

**Twin mode** adds the ten repeated-digit keys (`0000`, `1111`, up to `9999`) to the target list without replacing what is already typed. Twin mode plus two typed keys means twelve accepted values and the first to appear wins. Twin mode on an empty field means only those ten keys are accepted.

---

## Calibrating Buttons

If the accessibility tree of the target app does not expose the button labels, the engine cannot locate the buttons automatically. Tap **Set titik** in the panel, then tap the generate button once in the target app. With **Auto save** on, also tap the save button as the second point. Both coordinates are saved and reused from that point on. This makes the engine work on apps that draw their UI on a canvas or inside a web view.

---

## Stopping the Engine

Four independent ways to stop the engine so the panel can never block the screen:

- The **Stop** button in the floating panel
- Long press the bubble to stop the engine and hide the panel
- The **Stop** action in the foreground service notification
- Closing the panel with the **X** button, which leaves the bubble visible

---

## How It Works

The engine tries two read paths in order:

**Path 1 - Accessibility node:** The service walks the active window and looks for a node whose text is exactly four digits or four digits separated by spaces. This is fast and exact when the target app exposes its views through the accessibility tree.

**Path 2 - Screen vision:** If no node matches, the service takes a screenshot via `AccessibilityService.takeScreenshot()`, converts it to grayscale, applies Otsu thresholding, extracts connected pixel components, clusters them into rows, and slides a four-wide window across each row. Each candidate glyph is normalised to a 16x24 bitmap and matched against a runtime-generated template bank using a combination of L1 distance and intersection-over-union with an aspect-ratio penalty. The scan runs at two polarities (bright text on dark background and dark text on bright background) and takes the best-scoring candidate. A minimum confidence threshold must be met before the result is accepted.

Once a key is found and it matches the target, the key is read a second time to confirm it is stable. Only then does the engine act.

The scan region is cached after the first successful hit and reused on later passes, keeping each pass in the low hundreds of milliseconds.

---

## Features

### Dual Read Path
The engine always tries the accessibility tree first for speed and accuracy. It falls back to the screen vision engine automatically when the target app does not expose accessible text nodes.

### Vision Engine
The screen vision engine uses grayscale conversion, Otsu thresholding, connected component extraction, row clustering, and glyph template matching to extract the key from a screenshot. Digit templates are generated at runtime from the platform font and warmed on a background coroutine at startup so the first scan is not slow.

### Auto Generate and Match
The engine taps the generate button in the target app repeatedly, reading the key after each tap, until a key matches the target list or the attempt limit is reached.

### Auto Save Toggle
With Auto save on, the engine taps the save button in the target app when a match is found. With it off, the engine stops on the matching key and leaves saving to the user.

### Floating Overlay
A floating bubble and a collapsible panel appear over all other apps. The panel shows the last read key, its source, attempt count, confidence, and a scrollable log. It can be collapsed to the title bar only to save screen space. Position is saved and restored across sessions.

### Multi-Target and Twin Mode
Up to 16 target keys can be entered at once. Twin mode adds all ten repeated-digit keys to the target list. The engine stops at the first value from the union of all targets.

### Set Titik Calibration
Button coordinates can be set manually by tapping the actual buttons in the target app. This allows the engine to work on apps that render UI in a canvas, a game engine, or a web view where the accessibility tree has no useful nodes.

### History
Every key the engine stopped on is saved to the history list and viewable from the panel.

### Four Stop Methods
The engine can be stopped from the panel, by long pressing the bubble, from the notification, or by closing the panel with X. No single stop path can be blocked by another app.

---

## Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Floating bubble and panel overlay |
| `FOREGROUND_SERVICE` | Keep the panel alive in the background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required for foreground service on Android 14 |
| `POST_NOTIFICATIONS` | Foreground service notification |
| `VIBRATE` | Optional haptic feedback on a key match |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent the system from killing the panel |
| `BIND_ACCESSIBILITY_SERVICE` | Read the active window and dispatch taps |

No network permission is declared. The app does not connect to the internet or send any data anywhere.

---

## File Structure

```text
YWhatsapp-Key/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/yuurisan/ywhatsappkey/
│           ├── App.kt                       # Application class, global context
│           ├── MainActivity.kt              # Permission checklist and Start button
│           ├── core/store/
│           │   ├── HistoryStore.kt          # Persisted key history
│           │   └── Prefs.kt                 # SharedPreferences wrapper
│           ├── engine/
│           │   ├── Engine.kt                # Main engine: generate, scan, match, act
│           │   └── DigitWarm.kt             # Background glyph template warm-up
│           ├── input/
│           │   └── TapService.kt            # Accessibility service, tap dispatch, screenshot
│           ├── overlay/
│           │   ├── FloatingService.kt       # Foreground service managing bubble and panel
│           │   ├── BubbleView.kt            # Draggable floating bubble
│           │   ├── PanelController.kt       # Panel UI state and controls
│           │   └── OverlayBounds.kt         # Screen and safe area bounds helper
│           └── vision/
│               ├── KeyScanner.kt            # Screenshot scan: grayscale, threshold, component extract
│               └── DigitSolver.kt           # Glyph template bank, L1 plus IoU matcher
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── package.sh                               # Build and package script
├── LICENSE                                  # GNU AGPL v3.0
└── README.md
```

---

## Disclaimer

This software is provided for personal and educational use, on devices and accounts you own or are authorised to operate. Automating a third-party application may violate its terms of service and may result in rate limits, suspension, or a permanent ban of the account involved. You accept that risk entirely. The authors are not affiliated with WhatsApp, Meta, or any application this tool is pointed at.

---

<div align="center">
<img width="100%" alt="footer" src="https://capsule-render.vercel.app/api?type=waving&height=120&section=footer"/>
</div>