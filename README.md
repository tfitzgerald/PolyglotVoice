# 🎙️ Polyglot Voice
![Build Status](https://github.com/tfitzgerald/PolyglotVoice/actions/workflows/main.yml/badge.svg)

**Polyglot Voice** is a modern Android application designed for seamless, real-time conversation between Spanish and English speakers using on-device AI.

---

## ✨ Features
* **Auto-Language Detection:** Switches between ES and EN automatically.
* **Offline AI:** Works without internet after initial model sync.
* **Visual Feedback:** Dynamic Gold (ES) and Blue (EN) pulse animations.
* **Automated Builds:** Every commit generates a fresh APK via GitHub Actions.

... (rest of the instructions provided previously)

Polyglot Voice: Two-Way Offline AI Translator
Polyglot Voice is a modern Android application designed for seamless, real-time conversation between Spanish and English speakers. Built for 2026, it leverages on-device AI to provide instant voice-to-voice translation without requiring a persistent internet connection.

✨ Key Features
🤖 Auto-Language Detection: No more manual toggling. The app automatically detects whether you are speaking Spanish or English and translates to the other language.

📶 100% Offline Core: Uses Google ML Kit's on-device translation models. After an initial sync, it works perfectly in airplane mode or remote locations.

🌈 Visual Pulse Animation: Dynamic, color-coded ripples provide instant feedback. Gold for Spanish detection and Blue for English.

📜 Live Transcript: Maintains a scrollable history of your conversation for easy review.

📤 Shareable Logs: Export your full conversation transcript via SMS, Email, or WhatsApp with a single tap.

⚙️ Self-Healing Models: Includes a "Model Reset" utility to wipe and re-download AI models if they become corrupted.

🚀 Getting Started
Prerequisites
Android Device running Android 7.0 (API 24) or higher.

100MB of free storage for offline AI models.

Internet connection (Required for the first launch only to sync AI models).

Installation
Download the latest app-release.apk.

Enable "Install from Unknown Sources" in your Android settings.

Open the app and wait for the "Syncing Offline AI" bar to disappear.

🛠️ Build & Development
This project is optimized for Cloud Builds (e.g., Codemagic, GitHub Actions).

Local Setup
Clone the repository: git clone https://github.com/your-username/polyglot-voice.git

Open in Android Studio (Ladybug or newer).

Sync Gradle and run on a physical device (Emulators often lack high-quality microphone support).

Cloud Build Configuration (Codemagic)
Build Variant: release

Node/Java Version: Java 17

Artifact Path: app/build/outputs/apk/release/*.apk

📁 Project Structure
Plaintext
/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/translator/  # Kotlin Source Code
│   │   ├── res/layout/activity_main.xml  # Master UI
│   │   └── res/menu/main_menu.xml       # Settings & Share
│   └── build.gradle.kts                 # Optimized Build Config
├── gradle/wrapper/                       # Standard Gradle Engine
├── build.gradle.kts                      # Project-level plugins
└── settings.gradle.kts                   # Module definitions
📜 License
Distributed under the MIT License. See LICENSE for more information.