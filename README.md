# Table Clock

Table Clock turns an Android phone into a dim table clock when a magnetic charger is holding it flat or on its side.

The same pad can stay unplugged. Wireless charging is slow and warms the phone, but the magnets still make a good stand. The app watches the magnetic field. When the phone is lying still in that field, the screen wakes into a large clock. Pick the phone up and the clock goes away.

If the pad is powered, wireless charging opens the clock as well.

## What you need

- Android 14 or newer
- A phone with a magnetometer. A Galaxy S24 Ultra is one.
- A magnetic charger or a magnetic stand

The app screen shows a live preview. Choose Classic, Digital, or both, then a style and a size for each. Date, temperature, and the side notes have their own sizes, and each of those can be hidden. The weather icon follows the forecast. The open space on the left shows the next alarm. The space on the right shows how warm the phone is, and the battery under that.

## Use it

1. Install the app and open it.
2. Turn on **Listen for the charger**.
3. Allow the four requests: notifications, show over other apps, full-screen alerts, and unrestricted battery. The first three let the clock appear over the lock screen. The last one stops the phone from pausing the listener overnight.
4. Allow location if you want the weather. It uses an approximate position and [Open-Meteo](https://open-meteo.com/), with no account. The forecast is cached for half an hour.
5. Set the phone on the charger, flat or landscape.

Brightness starts low, so the screen is not another source of heat. Tap the clock to change it, or to close it. Closing it while the phone is still docked keeps the clock closed until you pick the phone up.

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
