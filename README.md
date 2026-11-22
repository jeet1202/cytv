# CleanYourTV Mond Launcher (`com.gathin.tvhome`)

Custom modification of the open-source **CleanYourTV** launcher that replaces the stock Apple TV-style clock with the **Mond Clock Widget** (featuring the futuristic **Anurati** font for the day and **Quicksand** for the date and hyphenated time).

---

## What was modified

1. **Fonts Added (`app/src/main/res/font/`)**:
   - `anurati.otf`: Extracted directly from `mond.rmskin` for the geometric day text.
   - `quicksand.otf`: For the date and time (`- HH:MM AM/PM -`).
2. **Font References (`app/src/main/res/values/fonts.xml`)**:
   - Registered `font_anurati` and `font_quicksand`.
3. **Home Screen Clock (`app/src/main/res/layout/activity_main.xml`)**:
   - Hid the stock greeting, thin clock, and divider.
   - Inserted the Mond Clock layout:
     - **Day**: In large Anurati uppercase font.
     - **Date**: Uppercase Quicksand date (`MMMM d, yyyy`).
     - **Time**: Uppercase Quicksand time with hyphens (`- h:mm a -` / `- HH:mm -`).
4. **Screensaver Clock (`app/src/main/res/layout/dream_clock.xml`)**:
   - Replaced the screensaver clock with the Mond Clock, complete with anti-burn-in drifting.

---

## How to Build the APK

### Option A: Cloud Build with GitHub Actions (Easiest — No Local Setup)
1. Push this folder to a GitHub repository (e.g. `your-username/cleanyourtv-mond-launcher`).
2. GitHub Actions will automatically detect `.github/workflows/build-apk.yml`, build the APK in ~1 minute, and make it available under the **Actions** tab as an artifact download (`cleanyourtv-mond-launcher.apk`).

### Option B: Local Windows Build
1. Open PowerShell in this folder.
2. If you don't have JDK 17 installed yet, run:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\build-mond-apk.ps1 -InstallJdk
   ```
3. Once built, the APK will be placed at `cleanyourtv-mond-launcher.apk`.

---

## How to Install on your Android TV
Once you have `cleanyourtv-mond-launcher.apk`, connect your TV over ADB (or use a USB drive):

```powershell
adb connect <your-tv-ip>:5555
adb install -r cleanyourtv-mond-launcher.apk
```
This updates the existing CleanYourTV launcher on your TV, replacing the old clock with the Mond Clock.
