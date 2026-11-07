# 🍔 Tasty Drive — Original Quality Telegram Cloud Backup 🚀

<div align="center">
  <img src="app/src/main/res/drawable/ic_tasty_logo.xml" alt="Tasty Drive Logo" width="128" height="128" />
  <h3>Super Fast, Lightweight &amp; Butter-Smooth Cloud Backup for Android</h3>
  <p>Back up all your Photos, Videos, APKs, PDFs, and Documents to your own Telegram Channel in 100% original uncompressed quality!</p>
</div>

---

## 🌟 Highlights & Features

- 🏎️ **Native C Engine Core (`libtastydrive.so`)**:
  - High-performance C11 core compiled via Android NDK for `ARM64-v8a`, `ARMeabi-v7a`, and `x86_64`.
  - Zero-overhead 64-bit streaming checksum for instant duplicate detection.
  - Native 64KB chunk buffer optimizer for maximum network throughput.

- 📱 **Android 9.0+ Support (API 28 to 35+)**:
  - Full compatibility with modern Android 13/14 Scoped Storage and All Files Access (`MANAGE_EXTERNAL_STORAGE`).
  - Works seamlessly across Android 9, 10, 11, 12, 13, 14, and 15!

- 🧈 **Butter-Smooth 120Hz Material 3 UI**:
  - Gourmet dark canvas theme with warm cheddar amber, fire coral, and mint accents.
  - Funny and creative burger mascot with cool sunglasses and racing wheels!
  - Fully virtualized queue with view recycling: 0% lag, even with 50,000+ files.

- 🤖 **Telegram Bot & Channel Backup**:
  - Uploads media as uncompressed documents (`sendDocument`) to ensure Telegram does not recompress or downscale your files.
  - Preserves 100% original resolution, EXIF metadata, bitrate, and quality.

- 🔄 **Persistent Background Foreground Service**:
  - Runs with Android `ForegroundService` (`DATA_SYNC` type) and Partial WakeLock.
  - **Keeps uploading even when swiped away / cleared from recent apps!**
  - Notification controls: live progress bar, upload speed (`MB/s`), Pause, Resume, and Stop actions.

- 📁 **50 MB Bot Limit Router & Large Files Tab**:
  - Telegram Bot API restricts automated bot uploads to 50 MB.
  - Any file larger than 50 MB is automatically categorized and sent to the **Large Files (>50MB)** tab.
  - 1-click **"🚀 Share to Telegram App"** button opens the official Telegram app via `FileProvider` to send files up to **2 GB / 4 GB** directly!

- 📦 **All File Types Supported**:
  - 📸 Photos (`.jpg`, `.png`, `.heic`, `.webp`)
  - 🎥 Videos (`.mp4`, `.mkv`, `.mov`)
  - 🤖 Android APKs (`.apk`)
  - 📄 PDF Documents (`.pdf`)
  - 📑 Documents & Archives (`.doc`, `.docx`, `.txt`, `.zip`, `.rar`)
  - 🎵 Audio (`.mp3`, `.m4a`, `.flac`)

- ⚡ **Multi-stream Concurrent Uploads & Network Controls**:
  - Configurable 1x to 4x simultaneous streams.
  - Network constraints: `Both (Wi-Fi or Mobile)`, `Wi-Fi Only`, or `Mobile Data Only`.
  - Automatic rate-limit handling (HTTP 429 backoff).

---

## 📥 Direct APK Download

Download the ready-to-install signed APK directly:
👉 **[TastyDrive.apk](TastyDrive.apk)** (~12.5 MB)

---

## 🚀 How to Set Up & Use

1. **Create a Telegram Bot**:
   - Open Telegram and message [@BotFather](https://t.me/BotFather).
   - Send `/newbot`, follow instructions, and copy the **HTTP API Bot Token**.
2. **Create a Channel**:
   - Create a Telegram Channel (Public or Private).
   - Add your newly created bot to the Channel as an **Admin** with permission to post messages.
   - Copy the Channel ID (e.g. `-1001234567890`) or public username (`@yourchannel`).
3. **Configure in Tasty Drive**:
   - Open **Tasty Drive** -> **Settings** tab.
   - Paste your **Bot Token** and **Channel ID**.
   - Tap **"🔍 Test Bot"** and **"🔍 Test Channel"** to verify connection.
   - Tap **"💾 Save Telegram Settings"**.
4. **Start Backup**:
   - Go to the **Queue** tab and tap **"🚀 Start Uploads"**!
   - You can lock your phone or swipe the app away; Tasty Drive will continue backing up in the background!
     **বক্সে হাত দিয়ে লিখতে না চাইলে উপরে থাকা 🔍 Auto-Discover PC on Wi-Fi বাটনে একটা চাপ দিন—অ্যাপ নিজে থেকেই সঠিক আইপি http://192.168.0.190:8081 বক্সে বসিয়ে নিবে!**
  ** or add  your pc IP  manually **
     

---

## 🛠️ Building from Source

### Prerequisites:
- Android SDK (API 34+)
- Android NDK (`30.0.16248370` or `25+`)
- CMake `3.22.1`
- JDK 17 or JDK 21

### Build APK:
```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```
The output APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📜 License
Distributed under the MIT License. Built with ❤️ and high-performance Native C &amp; Kotlin.

