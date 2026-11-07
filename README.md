# JePlay — CarPlay for Jaecoo

[English](README.md) · [Русский](README.ru.md)

Maps, music and calls from your iPhone on the car screen — wireless CarPlay
for compatible Jaecoo head units, with turn arrows on the HUD.

**Official website:** [https://jeplay.ru](https://jeplay.ru)

## Features

- **Wireless CarPlay** — uses the car’s built-in mechanism; no extra dongles, USB modems or gadgets.
- **HUD arrows** — CarPlay navigator maneuvers on the instrument cluster, like the stock Jaecoo J8 navigator.
- **Cluster map** — scaled for the cluster (no tiny text); cursor shifted left so system prompts don’t cover the road. Scale and position are adjustable.
- **Now playing** — current CarPlay track and album art on the cluster.
- **Headrest navigation prompts** — voice guidance only in the driver’s headrest speakers.
- **Over-the-air updates** — the app checks jeplay.ru for new versions and downloads them.
- **Install from Chrome** — the install page connects over WebUSB/ADB, installs the package, grants permissions and applies required settings.

## Compatibility

Primary focus: **Jaecoo J8**, **Jaecoo J7**. Other CHERY-group models may behave differently; HUD arrows depend on a specific vehicle service.

JePlay is a normal Android app. The system partition is not reflashed — stock ADB and OS permissions are used.

## Install

Installation **must** be done through the official install page in Chrome:

**[https://jeplay.ru/install.html](https://jeplay.ru/install.html)**

Do not install by sideloading a random APK — the page enables ADB, installs the package, grants permissions and applies required settings.

You need:

- a laptop with **Google Chrome** (WebUSB does not work in Safari / Firefox); or an Android phone with OTG and Chrome;
- a **USB Type-A ↔ USB Type-A** data cable (not charge-only);
- a USB stick and the flag file for the engineering menu / ADB;
- the install page opened at **https://jeplay.ru/install.html** (not `file://`).

The car’s USB-C port is often charge-only — use the USB-A port normally used for Android Auto / CarPlay.

### 1. USB flag stick

1. Download [`svengmode.flag`](jaecoo/site/assets/svengmode.flag) and copy it to the root of the stick.
2. Plug the stick into the car’s USB port.
3. Open the engineering menu → **USB2.0** → bottom button **DEVICE**.
4. ADB should activate. Remove the stick and connect the cable to the laptop.

If there is no DEVICE button — enable ADB via the ENCRYPTION menu (password calculator on the [install page](https://jeplay.ru/install.html)) and ADB Switch → Open.

### 2. ENCRYPTION password

Check the encryption version on the car screen. Enter **only the password** from the website form into the car — not the productID and not the VIN. Calculation runs locally in the browser.

- **Version 1** — last 6 or 7 digits of productID (keep leading zeros).
- **Version 2** — productID (full or last 6 digits) and ranNum / RAND (4 digits).

For ENCRYPTION 3 / 3.1 on some cars, open Temperatura → Confirm first (switches to version 2), then ENCRYPTION again. Or use the flag USB stick.

### 3. Connect and install

1. Cable: car USB-A ↔ laptop.
2. Open **[https://jeplay.ru/install.html](https://jeplay.ru/install.html)**, press **Connect** and pick the device in Chrome.
3. If the head unit asks about an RSA key — allow “Always”.
4. The button becomes **Install**: the latest build is downloaded (`version.txt` → `download/JePlay-<version>.apk`), installed over ADB, and permissions are granted (including updates from JePlay).

After install, SoftAP network name and password may be read and stored so the first launch can pick them up automatically.

## Updates

- In the app: **Settings → About → check for updates** (jeplay.ru).
- Or open the [install page](https://jeplay.ru/install.html) again — it pulls the latest version and installs over the existing one.

For web install, internet is needed on the laptop, not on the head unit. For in-app updates, either put the head unit online or use the laptop again.

## Pairing the iPhone with CarPlay

If the phone is already bound to the system CarPlay:

1. Unpair it in the head-unit settings.
2. Remove it from known Bluetooth devices.
3. Pair again, but **decline** when asked to use CarPlay.
4. Open JePlay, pick the phone in the Bluetooth list — the app will connect.

## Support the author

If you like the app, you can [buy the author a coffee](https://yoomoney.ru/to/4100118846441468/).

## Credits

JePlay is based on the open-source project [DiPlay](https://github.com/shihabal3amri/DiPlay)
(CarPlay client for Android head units; GPL-3.0 / AGPL-3.0 for the UI part).

Jaecoo-specific overlay, site and developer notes: [`jaecoo/`](jaecoo/).
