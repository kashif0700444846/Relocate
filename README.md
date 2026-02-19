# 📍 Relocate — Location Changer for Android

**Spoof your GPS location on any Android app. Change your geolocation to anywhere in the world with a single tap.**

> Also available as a [Chrome Extension](https://github.com/kashif0700444846/relocate-extension)

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🗺️ **Interactive Map** | Tap anywhere on the map to set your location |
| 🔍 **Address Search** | Search any address or city with live autocomplete |
| 📍 **Quick Presets** | Save and manage your favourite locations |
| 🕐 **Recent Locations** | Quickly reuse your last 8 locations |
| 🎯 **Accuracy Control** | Adjust GPS accuracy from 1m to 100m |
| 🛣️ **Route Simulation** | Simulate movement along a route with OSRM |
| 🌗 **Dark/Light Theme** | Beautiful UI in both modes |
| 📱 **Standard Mode** | Uses Android Mock Location (no root needed) |
| 🔓 **Root Mode** | Uses SU for undetectable spoofing |

---

## 🛡️ Dual Spoofing Modes

### Standard Mode (No Root)
- Uses Android's built-in `MockLocationProvider` API
- Requires enabling **Developer Options → Select Mock Location App**
- ⚠️ **Warning:** Detectable by apps that check `isFromMockProvider()`

### Root Mode (Undetectable)
- Uses `su` commands to inject location at system level
- Removes mock location indicators via reflection
- ✅ **Virtually undetectable** by ride-hailing and navigation apps
- Requires a rooted device with Magisk/KernelSU

---

## 📦 Installation

### From GitHub Releases
1. Go to [Releases](https://github.com/kashif0700444846/Relocate/releases)
2. Download the latest `.apk` file
3. Install on your Android device (enable "Install from Unknown Sources")

### Build from Source
```bash
git clone https://github.com/kashif0700444846/Relocate.git
cd Relocate
./gradlew assembleDebug
# APK will be at app/build/outputs/apk/debug/
```

---

## 🚀 Usage

1. **Open Relocate** → Choose your spoofing mode (Standard or Root)
2. **Set location** via map tap, address search, or preset
3. **Tap "Apply"** → Your location is now spoofed!
4. **Open any app** → It will see your fake location
5. **Tap "Real Location"** to restore your actual GPS

### Route Simulation
1. Go to **Settings → Route Simulation**
2. Add 2+ waypoints (search by address)
3. Choose mode (Driving/Walking), direction, and speed
4. Press **Start** → Watch your location move along the route!

---

## 🏗️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3
- **Map:** OSMDroid (OpenStreetMap)
- **Search:** Nominatim API
- **Routing:** OSRM API
- **Storage:** DataStore + SharedPreferences
- **CI/CD:** GitHub Actions

---

## 📄 License

Apache License 2.0

---

**Made with ❤️ by [kashif0700444846](https://github.com/kashif0700444846)**
