# Table Clock

Table Clock turns an Android phone into a dim table clock when a magnetic charger is holding it flat or on its side.

The same pad can stay unplugged. Wireless charging is slow and warms the phone, but the magnets still make a good stand. The app watches the magnetic field. When the phone is lying still in that field, the screen wakes into a large clock. Pick the phone up and the clock goes away.

If the pad is powered, wireless charging opens the clock as well.

Version 2.3 is the finished app for now.

## What you need

- Android 14 or newer
- A phone with a magnetometer. A Galaxy S24 Ultra is one.
- A magnetic charger or a magnetic stand

## The setup screen

The app screen shows a live preview of the same clock the dock uses. Weather still comes from the forecast, not from a manual picker.

- **Show:** Classic, Digital, or both.
- **Classic:** Fine, Numbers, Index, or Dots, then a size slider.
- **Digital:** Light, Bold, Stacked, or Contrast, then a size slider. Contrast draws the minutes in the weather color.
- **Hours:** follow the phone, or force 12-hour or 24-hour.
- **Switches:** seconds, date, weather, the next alarm, and the phone temperature.
- **Size sliders** under date, weather, and the side notes.

Brightness starts low, so the screen is not another source of heat.

## The clock

The clock sits in the middle.

The left side shows the next alarm from the phone's clock, with a small bell. If nothing is set, it says None. Android does not let this app snooze another app's alarm.

The right side shows how warm the phone is, with a thermometer, and the battery under that. A bolt means it is charging. The number turns amber at 40°C. Temperatures include °C or °F, following the phone's region.

A small drawing of the phone sits in the lower left, named for the model. On a Galaxy S24 Ultra it says Galaxy S24 Ultra.

Tap the clock to open the controls. Brightness is the bar along the bottom. The vertical slider on the right sets a timer, up to 90 minutes. Slide up for more time, then let go. While it runs, that slider drops and the time at the bottom counts down. At zero the phone plays the alarm sound until you tap the screen.

Closing the clock while the phone is still docked keeps it closed until you pick the phone up.

## Use it

1. Install the app and open it.
2. Turn on **Listen for the charger**.
3. Allow notifications, show over other apps, full-screen alerts, and unrestricted battery. The first three let the clock appear over the lock screen. The last one stops the phone from pausing the listener overnight.
4. Allow location if you want the weather. It uses an approximate position and [Open-Meteo](https://open-meteo.com/), with no account. The forecast is cached for half an hour.
5. Set the phone on the charger, flat or landscape.

The line under the field meter says when the magnet is strong enough. Calibration is optional. Use it only if a magnetic case opens the clock while the phone is still in your hand: measure in your hand, then on the charger.

## How it decides

Either signal is enough, as long as the phone is flat or on its side and not moving.

| Signal | When it counts |
|---|---|
| Magnet | The field is about 75 µT or stronger. Earth's field is lower than that, so a charger magnet stands out. |
| Wireless power | The pad is charging the phone. |

A foreground service reads the magnetometer, gravity, and gyroscope. Those readings stay on the phone. Weather sends an approximate latitude and longitude to Open-Meteo and nothing else.

## Build

You need JDK 17 or newer and Android SDK 34.

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it from the phone's Files app, or with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` points at your SDK and is not part of the repo. Android Studio creates it.

## License

[MIT](LICENSE)
