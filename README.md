# 📍 Relocate — GPS Location Changer for Android

**Spoof your GPS location on any Android app. Change your geolocation to anywhere in the world with a single tap. Drive back smoothly when done — no teleportation detected.**

> Also available as a [Chrome Extension](https://github.com/kashif0700444846/relocate-extension)

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🗺️ **Interactive Map** | Tap anywhere on the map to set your location |
| 🔍 **Address Search** | Search any address or city with live autocomplete |
| 📌 **My Location** | One-tap button to center on your real GPS position |
| 📍 **Quick Presets** | Save and manage your favourite locations |
| 🕐 **Recent Locations** | Quickly reuse your last 8 locations |
| 🎯 **Accuracy Control** | Adjust GPS accuracy from 1m to 100m |
| 🛣️ **Route Simulation** | Simulate movement along a route with OSRM |
| 🚗 **Drive Back** | Smoothly drive from spoofed location back to real GPS |
| 🔒 **All-App Hook** | LSPosed module hides spoofing from ANY app |
| 🌗 **Dark/Light Theme** | Beautiful UI in both modes |
| 📱 **Standard Mode** | Uses Android Mock Location (no root needed) |
| 🔓 **Root Mode** | Uses SU for undetectable spoofing |

---

## 🛡️ Architecture

Relocate works on **two layers**:

### Layer 1: GPS Spoofing (runs in Relocate)
Injects fake GPS coordinates into the Android location system.

| Mode | How it Works | Root Required? |
|------|-------------|----------------|
| **Standard** | Uses `MockLocationProvider` API | No |
| **Root** | Uses `su` to write directly to GPS system | Yes |

### Layer 2: Detection Bypass (LSPosed Hook)
Runs **inside target apps** to hide ALL evidence of spoofing:

- ✅ `isFromMockProvider()` → always returns `false`
- ✅ `isMock` / `isMocked` fields → hidden
- ✅ Root apps (Magisk, SuperSU) → invisible to `PackageManager`
- ✅ `su` / `busybox` binaries → `File.exists()` returns `false`
- ✅ Developer Options → appears disabled
- ✅ USB Debugging → appears disabled
- ✅ `Build.TAGS` → shows `release-keys` (not `test-keys`)

---

## 📦 Installation

### Step 1: Install Relocate APK
1. Download the latest APK from [Releases](https://github.com/kashif0700444846/Relocate/releases)
2. Install on your rooted Android device

### Step 2: Enable LSPosed Module
1. Open **LSPosed Manager**
2. Go to **Modules** → Find **Relocate**
3. Enable it ✅
4. Under **Scope**, check the apps you want to spoof (e.g., Uber Driver, Bolt, etc.)
5. Reboot your device

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
   - 🗺️ Tap on the map
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

### 🛣️ Route Simulation

For advanced use — simulate movement along any custom route:

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
| **GitHub Actions** | CI/CD |

---

## 📋 Version History

| Version | Changes |
|---------|---------|
| **v1.5.0** | 🚗 Drive Back feature, 📝 Updated README |
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
