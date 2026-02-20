# 📍 Relocate — GPS Location Changer for Android

**Spoof your GPS location on any Android app. Change your geolocation to anywhere in the world with a single tap. Drive back smoothly when done — no teleportation detected.**

> Also available as a [Chrome Extension](https://github.com/kashif0700444846/relocate-extension)

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🗺️ **Interactive Map** | Pan & zoom with single finger. Tap to set location |
| 🔍 **Address Search** | Search any address or city with live autocomplete |
| 📌 **My Location** | One-tap button to center on your real GPS position |
| 📍 **Quick Presets** | Save and manage your favourite locations |
| 🕐 **Recent Locations** | Quickly reuse your last 8 locations |
| 🎯 **Accuracy Control** | Adjust GPS accuracy from 1m to 100m |
| 🛣️ **Route Simulation** | Simulate movement along a route with OSRM |
| 🚗 **Drive Back** | Smoothly drive from spoofed location back to real GPS |
| 🔒 **16-Hook Detection Bypass** | LSPosed module hides spoofing from ANY app |
| 🔧 **App Fixer** | Per-app identity reset with selective vector control |
| 📺 **Live Hook Console** | Real-time hook activity monitoring with color-coded entries |
| 🌗 **Dark/Light Theme** | Beautiful UI in both modes |
| 📱 **Standard Mode** | Uses Android Mock Location (no root needed) |
| 🔓 **Root Mode** | Uses SU for undetectable spoofing |

---

## 🛡️ Architecture

Relocate works on **three layers**:

### Layer 1: GPS Spoofing (runs in Relocate)
Injects fake GPS coordinates into the Android location system.

| Mode | How it Works | Root Required? |
|------|-------------|----------------|
| **Standard** | Uses `MockLocationProvider` API | No |
| **Root** | Uses `su` to write directly to GPS system | Yes |

### Layer 2: Detection Bypass (16 LSPosed Hooks)
Runs **inside target apps** to hide ALL evidence of spoofing:

**Location Hooks (v1.2.0):**
- ✅ Hook 1-2: `isFromMockProvider()` / `isMock` → always `false`
- ✅ Hook 3: Mock location providers → hidden from `getProviders()`
- ✅ Hook 4: `ALLOW_MOCK_LOCATION` setting → returns `0`
- ✅ Hook 5: Coordinates → injected from SharedPreferences

**Root Detection Hooks (v1.3.0):**
- ✅ Hook 6: `Build.TAGS` → shows `release-keys`
- ✅ Hook 7-8: Developer Options & USB Debugging → appear disabled
- ✅ Hook 9: Root apps (Magisk, SuperSU) → invisible to PackageManager
- ✅ Hook 10: `su`/`busybox` binaries → `File.exists()` returns `false`
- ✅ Hook 11: `ExtraDeviceInfo.isRooted` → returns `false`

**Identity Spoofing Hooks (v1.5.0+):**
- ✅ Hook 12: Widevine DRM ID → spoofed `deviceUniqueId`
- ✅ Hook 13: `Settings.Secure.android_id` → randomized

**Anti-Detection Hooks (v1.8.0):**
- ✅ Hook 14: Google Advertising ID (GAID) → randomized UUID
- ✅ Hook 15: `Build.FINGERPRINT` + `DISPLAY` + `HOST` → stock device values
- ✅ Hook 16: Chrome CookieManager → strips Uber tracking cookies

### Layer 3: App Fixer (Identity Reset)
Per-app panel to selectively regenerate device identity vectors:
- Android ID, DRM ID, GAID, Build Fingerprint
- Chrome cookie clearing (Uber domains only)
- GMS cache reset, AppOps reset

---

## 📦 Installation

### Step 1: Install Relocate APK
1. Download the latest APK from [Releases](https://github.com/kashif0700444846/Relocate/releases)
2. Install on your rooted Android device
3. Open app → Grant all permissions when prompted

### Step 2: Enable LSPosed Module
1. Open **LSPosed Manager**
2. Go to **Modules** → Find **Relocate**
3. Enable it ✅
4. Under **Scope**, check the apps you want to spoof (e.g., Uber Driver, Bolt, etc.)
5. **Reboot** your device

### Build from Source
```bash
git clone https://github.com/kashif0700444846/Relocate.git
cd Relocate
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/
```

---

## 🚀 How to Use

### Basic Spoofing

1. **Open Relocate**
2. **Set your fake location** using one of:
   - 🗺️ Tap on the map (single-finger pan, pinch to zoom)
   - 🔍 Search an address in the search bar
   - 📍 Select a saved preset
   - 📌 Tap the blue GPS button on map to go to your real location first
3. **Choose a mode:** Standard or Root (Standard works fine with LSPosed)
4. **Tap "✅ Apply"** → Your location is now spoofed!
5. **Open Uber/Bolt/etc** → They see your fake location
6. **Tap "🔄 Real"** to instantly restore your real GPS

### 🚗 Drive Back (Smooth Return)

**Problem:** If you spoof to a location 5km away, then suddenly stop spoofing, apps detect a 5km "teleportation" — which flags you as using a fake location app.

**Solution:** The **Drive Back** button simulates driving from your spoofed location back to your real GPS position at 80 km/h along actual roads.

**How to use:**
1. You're spoofing at a fake location → you accept a ride
2. Before turning off spoofing, tap **"🚗 Back"**
3. Relocate:
   - Gets your **real GPS position** as the destination
   - Fetches a **real driving route** from OSRM (actual roads, not straight line)
   - **Simulates driving** at 80 km/h along the route
   - Shows progress: `🚗 Driving back... 45% (120/267)`
   - Shows the route on the map with a moving marker
4. When you **arrive** at your real position:
   - Spoofing **automatically stops** ✅
   - Real GPS is **restored** 📍
   - No location jump detected by any app

> **Note:** If you're already within 50 meters of your real position, it will just stop spoofing immediately — no simulation needed.

### 🔧 App Fixer (Identity Reset)

For apps like Uber that fingerprint your device:

1. Open **App Fixer** (🔧 icon in header)
2. Find the target app (Uber Driver is pinned at top ⭐)
3. Tap to expand → see current identity values
4. Check which identities to regenerate:
   - 📱 Android ID
   - 🔐 DRM ID (Widevine)
   - 🎯 Google Ad ID
   - 🔑 Build Fingerprint
   - 🍪 Chrome Cookies (Uber domains)
5. Tap **"🔧 Apply Selected Fixes"**
6. Reboot → Open Uber → Device appears as new

### 📺 Live Hook Console

Monitor hook activity in real-time:

1. Open **Settings** → **📺 Live Console**
2. Two tabs:
   - **App Logs** — Relocate's own activity (spoof start/stop, permissions)
   - **Hook Activity** — Real-time entries from XPosed hooks running inside target apps
3. Hook entries are color-coded:
   - 🟢 Green = Location hooks
   - 🟠 Orange = Root detection hooks
   - 🔵 Blue = Identity hooks
   - 🟣 Purple = v1.8 hooks (GAID, fingerprint, cookies)

### 🛣️ Route Simulation

Simulate movement along any custom route:

1. Open the **Route Simulation** section
2. Search **Start** and **End** locations
3. Choose mode: Driving or Walking
4. Set speed (km/h)
5. Press **▶️ Start** → Watch your location move along the route
6. Use **⏸️** to pause or **⏹️** to stop

---

## 🔧 Setup Guide

### For Standard Mode (No Root)
1. Enable **Developer Options** on your phone
2. Go to **Developer Options → Select Mock Location App**
3. Choose **Relocate**
4. Open Relocate → Select "Standard" mode → Apply

### For Root Mode
1. Must have **Magisk** or **KernelSU** installed
2. Open Relocate → Select "Root" mode → Apply
3. Grant root permission when prompted

### LSPosed Hook (Recommended)
Required to hide spoofing from apps like Uber, Bolt, Lyft, Google Maps.

1. Install **LSPosed** (via Magisk module)
2. Open LSPosed Manager → **Modules** → Enable **Relocate**
3. Under **Scope**, select the apps you want to hide spoofing from
4. **Reboot** your device
5. The hook will now hide all mock location flags, root detection, and developer options from selected apps

> **Which spoofing mode with LSPosed?** Standard mode works perfectly fine when LSPosed hooks are active. The hooks hide the mock flag that Standard mode creates. Root mode adds an extra layer of stealth but isn't required.

### For Uber Anti-Detection
See the full step-by-step guide: **[SETUP_GUIDE.md](SETUP_GUIDE.md)**

---

## 🏗️ Tech Stack

| Technology | Purpose |
|-----------|---------|
| **Kotlin** | Language |
| **Jetpack Compose** | UI Framework |
| **Material3** | Design System |
| **OSMDroid** | Map (OpenStreetMap) |
| **Nominatim API** | Address Search |
| **OSRM API** | Route Calculation |
| **DataStore** | Local Settings |
| **XSharedPreferences** | Cross-process Hook Communication |
| **LSPosed/Xposed** | App Hooking Framework |
| **GitHub Actions** | CI/CD — Auto-release APK on push |

---

## 📋 Version History

| Version | Changes |
|---------|---------|
| **v1.8.2** | 🗺️ Single-finger map control, 🔐 Permission requests, 📺 Hook log fix, 📝 README update |
| **v1.8.0** | 🔧 App Fixer redesign, 📺 Live Hook Console, 🔑 Hooks 14-16 (GAID, Fingerprint, Cookies) |
| **v1.5.0** | 🚗 Drive Back feature, 🔐 DRM + android_id hooks |
| **v1.4.0** | 🌐 All-app hook support, 📌 My Location button, 🔧 Search crash fix |
| **v1.3.0** | 🔒 Root/mock detection bypass (11 hooks) |
| **v1.2.0** | 🛣️ Route simulation, 📍 Presets & Recent |
| **v1.1.0** | 🔓 Root mode, 🎯 Accuracy control |
| **v1.0.0** | 🗺️ Initial release — Map, Search, Standard Mode |

---

## 📄 License

Apache License 2.0

---

**Made with ❤️ by [kashif0700444846](https://github.com/kashif0700444846)**
