# 🛡️ Uber Anti-Detection Setup Guide (Relocate v1.8.0)

## PHASE 1: Initial Setup (One-Time)

### Step 1 — Install Relocate v1.8.0
Download and install the APK from [GitHub Releases](https://github.com/kashif0700444846/Relocate/releases).

### Step 2 — Enable LSPosed Module
1. Open **LSPosed Manager**
2. Go to **Modules** → find **Relocate** → toggle **ON** ✅
3. Tap **Scope** → check:
   - ☑️ `com.ubercab.driver` (Uber Driver)
   - ☑️ `com.android.chrome` (Chrome — needed for cookie isolation)
   - ☑️ Any other ride-hailing apps

### Step 3 — Grant Permissions
- Open Relocate → grant **Location** (Always)
- Go to **Settings → Apps → Relocate → Permissions** → grant **"All files access"**

### Step 4 — 🔄 REBOOT
> LSPosed hooks only activate after a reboot. Mandatory.

---

## PHASE 2: Reset Uber Identity (Before Opening Uber)

### Step 5 — Open Relocate → 🔧 App Fixer

### Step 6 — Tap "Uber Driver" → Expand Panel
You'll see current IDs. Keep **all checkboxes checked** ☑️:
- ☑️ Clear App Data
- ☑️ New Android ID
- ☑️ New DRM ID (Widevine)
- ☑️ New Google Ad ID
- ☑️ Spoof Build Fingerprint
- ☑️ **Clear Uber Chrome Cookies** ← critical
- ☑️ Clear Play Services Cache
- ☑️ Reset AppOps

### Step 7 — Tap "🔧 Apply Selected Fixes"
Wait for all ✅. Note the new IDs shown.

### Step 8 — 🔄 REBOOT again
> Ensures hooks load fresh spoofed values.

---

## PHASE 3: Start Spoofing

### Step 9 — Open Relocate (NOT Uber yet!)
- Set your location on the map → tap **Start**
- Confirm green "Spoofing Active" indicator

### Step 10 — Verify Hooks
- Go to **📺 Live Console** → **Hook Activity** tab
- Should see `INIT` + all 16 hooks listed

---

## PHASE 4: Open Uber Driver

### Step 11 — Open Uber Driver
- Login (data was cleared)
- Grant all permissions (overlay, notifications, location)

### Step 12 — Check Live Console
Switch to Relocate → 📺 → Hook Activity. Look for:
- `Hook12` DRM ID → spoofed ✅
- `Hook13` android_id → spoofed ✅
- `Hook14` GAID → spoofed ✅
- `Hook15` Build.FINGERPRINT → spoofed ✅
- `Hook16` Cookies stripped ✅
- `Hook01` isFromMockProvider → false ✅

### Step 13 — Drive! 🎉

---

## ⚡ Daily Use (After Initial Setup)

1. Open **Relocate** → set location → **Start**
2. Open **Uber Driver**
3. Done!

Only redo Phase 2 if Uber shows "Unable to authenticate device" again.

---

## 🚨 Troubleshooting

| Problem | Fix |
|---------|-----|
| "Unable to authenticate device" | Redo Phase 2 (all checkboxes) → Reboot |
| No hooks in Live Console | LSPosed → Modules → Relocate → check Scope → Reboot |
| Real location leaking | Check Relocate shows "Spoofing Active" |
| Play Integrity fails | Install **Play Integrity Fix** Magisk module |
| Still detected | Install **Shamiko** Magisk module to hide root |
