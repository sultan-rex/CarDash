# CarDash

A car dashboard for an old Android tablet (built for the Lenovo Tab 3 TB3-710I, Android 5.1).
Music, navigation, OBD-II gauges, and Android Auto built in as a single app.

## Build and install

Requirements: JDK 21, Android SDK platform 36, build-tools 36.1.0, NDK 29.0.14206865 and CMake 3.22.1
(Android Studio offers to install the last two on first sync).

    ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

Set `sdk.dir` in `local.properties` if Android Studio has not already.

## Setup on the tablet

1. **Home screen:** Settings (sliders icon) → *Set as home app* → choose CarDash, "Always".
2. **Now playing:** Settings → *Allow now-playing access* → tick CarDash.
3. **Gauges:** buy a Bluetooth ELM327 OBD-II adapter, plug it into the car's OBD port
   (usually under the steering wheel), pair it in Android Bluetooth settings (PIN 1234 or 0000),
   then in CarDash settings turn on *Use OBD-II adapter*, pick it, and turn *Demo mode* off.
4. **Navigation:** install an offline maps app (the tablet has no mobile data) and download
   your region's maps at home over Wi-Fi.
5. **Start with the car:** power the tablet from a USB port that switches on with the ignition.

## Android Auto

Android Auto is built in. The **Android Auto** tile opens the embedded head unit
([Open Headunit](https://github.com/andreknieriem/open-headunit)); no separate app is needed.
The first time it opens, it runs a short setup wizard.

The tablet only has 2.4 GHz Wi-Fi, so Android's official wireless Android Auto (which needs 5 GHz)
won't work. Open Headunit's Wi-Fi mode does work over the phone's hotspot:

1. On the phone, turn on the **mobile hotspot**, and set it to 2.4 GHz if it has the option.
2. On the phone, enable Android Auto developer mode (Android Auto settings → tap *Version* 10 times),
   then in the ⋮ menu choose **Start head unit server**.
3. Connect the tablet to the phone's hotspot, tap the **Android Auto** tile and pick the Wi-Fi connection mode.
4. In CarDash settings turn on *Auto-start wireless Android Auto* and enter the hotspot name.
   From then on, CarDash opens Android Auto automatically whenever the tablet joins that hotspot.

USB also works: plug the phone into the tablet with an OTG adapter and pick USB mode.

## Project layout

- `app/` – the CarDash app (Java).
- `headunit/` – Open Headunit, converted to an Android library (Kotlin and native code).
- `contract/` – Open Headunit's shared intent contract library.

Changes made to the upstream Open Headunit code: it builds as a library, its launcher icon,
home-screen alias and navigation-app intent filters are removed, and its native build is limited to
`armeabi-v7a` and `arm64-v8a`. CarDash's own `AppTheme`, `activity_main` and `ic_play` resources are
prefixed `cd_`/`Cd` so they do not override Open Headunit's.

## Licence

Open Headunit is licensed under the GNU Affero General Public License v3.0 (see `LICENSE` and
`COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`), so this combined project is distributed under the same
licence. If you give the app to anyone else you must also give them the source. "Android Auto" is a
trademark of Google.
