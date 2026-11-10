# Meteo Alarm Android

**Latest version:** see [Releases](https://github.com/viorelrb-alt/meteo-alarm-android/releases/latest) (test versions).

The app has been updated and now includes Farmer Mode, allowing you to monitor the weather and key metrics for your land plots.

The main screen shows the weather where you are: the clock, temperature, feels-like, wind, rain and snow for the next hours, the next days, sun, UV and pollen, together with the official weather warnings (MeteoAlarm in the EU, the National Weather Service in the US) and nearby earthquakes. Notifications tell you when rain, snow or a warning is coming. Home-screen widgets in several sizes, six colour themes (light and dark) and 28 languages are included.

If you get lost, the compass works offline and shows nearby towns and villages. You can also send your coordinates, together with a picture of the compass, so others can locate you. The villages you pass through are added to your county's list of towns and shown on the widgets too.

In Farmer Mode, the list of monitored indicators and alerts expands significantly. Whether you want to know when wind conditions are right for spraying your crops, soil-level temperature, humidity, or precipitation levels, all of these can be tracked in Farmer Mode for each land plot set up in the app, with statistics that can be saved as PDF.

All this information stays strictly on your device. No one else has access to it. Each farmer only tracks the locations they are interested in.

I added an option to export your app settings (towns, favourites, land plots and preferences, everything you have configured on one device) so you can easily import them onto a secondary Android device or when you switch to a new phone.

The app is currently available only in the EU and the US, and only works on Android devices. It updates itself from this page (☰ › Check for updates).

Developed by Viorel-R. B.

<p>
  <img src="docs/preview-a54.png" width="45%" alt="Light theme (Sky palette), v4.00">
  <img src="docs/preview-a54-dark.png" width="45%" alt="Dark theme (Lavender palette), v4.00">
</p>

![Landscape, dark theme (Sage palette), v4.00](docs/preview-a54-landscape-dark.png)

*v4.00 screens in English, PC previews drawn by the same code with sample data (`./build.sh preview [dark] [land] [dry] [warn]`).*

**Project evolution:** [docs/MeteoAlarmAndroid-evolution.pdf](docs/MeteoAlarmAndroid-evolution.pdf), a diagram of what each version added, from v0.1 to v1.71.

## Build

No Gradle and no Google SDK download needed; Ubuntu's Android tools are enough:

```sh
sudo apt-get install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner
./build.sh            # -> build/MeteoAlarmAndroid-v<version>.apk
./build.sh preview    # -> build/preview.png (draws the screen on a PC)
```

Signing: every version must be signed with the same key, or it will not install over the one on
the phone. The key is not in this repository: `KEYSTORE=/path/to/debug.keystore ./build.sh`.

## Releases

Put the signed APK and its notes in `dist/` (`MeteoAlarmAndroid-v<version>.apk`,
`notes-v<version>.md`, the version being `android:versionName` in `AndroidManifest.xml`) and push to
`main`: the Release workflow publishes them as the Release `v<version>`, which the app then offers
as an update.

## Install on the phone

Download the APK from [Releases](https://github.com/viorelrb-alt/meteo-alarm-android/releases/latest) on the phone, open it from **My Files**, and allow **Install unknown apps** for that
app when asked. If Play Protect warns about an unknown developer, choose **Install anyway**.

## Code

- `src/ro/meteoalarm/android/core/`: plain Java, no Android: data (`Weather`, `Warnings`), texts
  and the screen drawing (`Screen`, on a 758-unit wide canvas).
- `src/ro/meteoalarm/android/`: the Android activity, view and canvas.
- `preview/`: draws `Screen` with Java2D for a PNG preview.
