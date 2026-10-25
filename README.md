# CarDash

A car dashboard for an old Android tablet (built for the Lenovo Tab 3 TB3-710I, Android 5.1).
OBD-II gauges, a fuel log, and Android Auto built in as a single app.

The home screen has three pages you swipe between:

1. **Home:** clock, now playing, the Android Auto and Gauges tiles, and a row of engine readouts.
2. **Gauges:** an analog speedometer and the RPM, coolant, throttle, load and battery gauges, the fault-code reader, and a strip
   with today's top speed, this month's top speed, today's distance and a **Drives** calendar.
3. **Fuel:** a fill-up log with consumption and cost statistics.

## Build and install

Requirements: JDK 21, Android SDK platform 36, build-tools 36.1.0, NDK 29.0.14206865 and CMake 3.22.1
(Android Studio offers to install the last two on first sync).

    ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

Set `sdk.dir` in `local.properties` if Android Studio has not already.

## Setup on the tablet

1. **Home screen:** Settings (sliders icon) → *Set as home app* → choose CarDash, "Always".
2. **Now playing:** Settings → *Allow now-playing access* → tick CarDash.
3. **Gauges:** swipe left on the home screen (or tap *Gauges*) for the analog speedometer and engine gauges.
   Buy a Bluetooth ELM327 OBD-II adapter, plug it into the car's OBD port
   (usually under the steering wheel), pair it in Android Bluetooth settings (PIN 1234 or 0000),
   then in CarDash settings turn on *Use OBD-II adapter* and pick it. Until the adapter connects, the RPM,
   coolant and battery readouts show `--` and speed comes from GPS.
4. **Drives:** the app records each day's distance, top speed and time moving in the background (also while Android Auto is on
   screen). Speed comes from the OBD-II adapter when it is connected, otherwise from GPS, so turn on **Location** (GPS) in the
   tablet's settings if you have no adapter. Tap **Drives** on the Gauges page for a month calendar: each driven day shows its
   kilometres, and tapping a day shows distance, top speed, time moving and average speed, with totals for the month.
   Distances use the speed unit setting (km or miles). Turn recording off, or clear the history, in Settings -> Drives.
5. **Fuel:** swipe to the third page and tap *Add fill-up* each time you refuel (odometer, fuel added, total cost,
   and whether you filled to full). Consumption is worked out full-to-full, so partial fills are counted correctly.
   Long-press a fill-up to delete it. Units follow the speed unit setting (km, L, L/100 km, or mi, gal, mpg).
6. **Start Android Auto from Bluetooth:** the car icon in the top bar (next to Wi-Fi and Bluetooth) chooses which phones start
   Android Auto when they connect over Bluetooth: *Off*, *Chosen phones*, or *Any phone*. *Any phone* only matches devices
   Android classes as a phone, so a speaker or headset does not start it. The same choices are in Settings → Android Auto.
   Pair a new phone first; for *Chosen phones*, tick it in Settings.
7. **Wi-Fi and Bluetooth:** the two icons beside the OBD status in the top bar show their state (teal when connected,
   dim when off). Tap one to pick a saved network or a paired device, or to turn it on or off.
8. **Switch button:** while a phone is connected, a quarter-circle tab sits in a corner of the screen above everything else.
   It shows a home icon on the Android Auto screen and a car icon elsewhere; tap it to fade between Android Auto and CarDash.
   Both stay running, so switching does not restart the session. Press and drag it to move it; it docks in the nearest corner.
   Turn it off in Settings → Android Auto → *Switch button*.
9. **Start with the car:** power the tablet from a USB port that switches on with the ignition. When charging starts, CarDash
   wakes the screen and opens (Settings → Startup → *Start with the car*; a simple swipe lock screen is dismissed, a PIN is not).
   Leave the tablet asleep rather than switched off: an app cannot power on a tablet that is fully off, and this tablet has no
   setting to boot when a charger is connected.

## Android Auto

Android Auto is built in; no separate app is needed
([Open Headunit](https://github.com/andreknieriem/open-headunit) provides the head unit). The **Android Auto** tile is
outlined in teal with a "Connected" line while a phone is connected. Tapping it opens the head unit, where you connect a
phone (USB or wireless), change its settings, or get back to the projection. The first time, it runs a short setup wizard.

### Wireless, started by Bluetooth

When a chosen (or any) phone connects over Bluetooth, the tablet starts Android Auto's wireless mode:

1. Bluetooth connects; the tablet recognises the phone from the auto-start list.
2. The tablet creates a private Wi-Fi Direct network and listens for the phone.
3. It wakes the phone over Bluetooth; the two swap the network's name and password over Bluetooth.
4. The phone joins that network, and video, audio and touch run over Wi-Fi.

So Bluetooth and Wi-Fi both need to be on, on the tablet and on the phone, and the phone needs wireless Android Auto
enabled. The tablet is on its own Wi-Fi Direct network for this, so it will not be on another Wi-Fi network at the same time.

### Over the phone's hotspot

The tablet only has 2.4 GHz Wi-Fi. If wireless mode does not work with a phone, Open Headunit's Wi-Fi mode also works over
the phone's hotspot:

1. On the phone, turn on the **mobile hotspot**, and set it to 2.4 GHz if it has the option.
2. On the phone, enable Android Auto developer mode (Android Auto settings → tap *Version* 10 times),
   then in the ⋮ menu choose **Start head unit server**.
3. Connect the tablet to the phone's hotspot, tap the **Android Auto** tile and pick the Wi-Fi connection mode.
4. In CarDash settings turn on *Auto-start wireless Android Auto* and enter the hotspot name.
   From then on, CarDash opens Android Auto automatically whenever the tablet joins that hotspot.

### USB

Plug the phone into the tablet with an OTG adapter. This needs no Bluetooth or Wi-Fi, and starts automatically when a
phone is plugged in.

### Music on the home screen

The *Now playing* card shows what is playing: your phone's track, artist and artwork while Android Auto is connected
(Open Headunit mirrors them into a media session on the tablet), or an app running on the tablet. Previous, play/pause
and next control whichever it is.

Over Bluetooth alone, a phone's music and track details cannot reach this tablet: its Bluetooth stack ships with the
receiving roles switched off. Android Auto is the way to get them.

## Project layout

- `app/` – the CarDash app (Java).
- `headunit/` – Open Headunit, converted to an Android library (Kotlin and native code).
- `contract/` – Open Headunit's shared intent contract library.

Notable pieces in `app/`: `MainActivity` (home, pager, Android Auto tile), `PagerView` (swipe pages), `SpeedoView` and
`GaugeView` (gauges), `FuelLog` and `FuelPage` (fuel), `ConnectControls` (Wi-Fi, Bluetooth and auto-start buttons),
`SwitchOverlayService` and `SwitchButtonView` (the switch button), `TripService`, `DriveLog`, `DrivesActivity` and
`CalendarGridView` (drive recording and the calendar), and `ObdManager` (ELM327 over Bluetooth).

Changes made to the upstream Open Headunit code:

- It builds as a library: no launcher icon, home-screen alias or navigation-app intent filters, native build limited to
  `armeabi-v7a` and `arm64-v8a`, and the upstream "github" flavor sources merged into `main`.
- The Android Auto screen runs in its own task (`taskAffinity` ending `.projection`), so it can be switched to and from
  CarDash without being destroyed.
- *Any phone* Bluetooth auto-start in `AutoStartReceiver` and `Settings`, and the `host/` helpers (`AutoStart`, `HostBridge`)
  that CarDash uses to choose phones, read the connection state and bring the projection forward.
- The phone's now-playing is mirrored into the media session by default.

CarDash's own `AppTheme`, `activity_main` and `ic_play` resources are prefixed `cd_`/`Cd` so they do not override Open
Headunit's.

## Licence

Open Headunit is licensed under the GNU Affero General Public License v3.0 (see `LICENSE` and
`COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`), so this combined project is distributed under the same
licence. If you give the app to anyone else you must also give them the source. "Android Auto" is a
trademark of Google.
