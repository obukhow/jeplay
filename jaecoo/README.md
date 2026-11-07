# Jaecoo overlay for DiPlay → JePlay

Local patches for Jaecoo J8. DiPlay upstream stays mostly untouched; restore after
an update by keeping this folder and the hooks marked `JAECOO`.

User-facing docs: [README.md](../README.md) · [README.ru.md](../README.ru.md)

## Branding & versioning (JePlay)

| Item | Value |
|------|--------|
| App name / APK | `JePlay` → artifact `JePlay-<versionName>.apk` |
| `applicationId` | `ru.jeplay.app` (not upstream `com.shihab.diplay`) |
| `versionName` | `0.2.21.33` — first three digits track DiPlay upstream; **fourth** is the JePlay-only patch |
| `versionCode` | `upstreamCode * 100 + jeplayPatch` (e.g. DiPlay `40` → `4013`) |

When merging a new DiPlay release (e.g. `0.2.22` / code `41`), bump the first three
version digits (and the upstream part of `versionCode`), then reset or advance the
fourth digit for JePlay-only changes.

## In-app updates (jeplay.ru)

1. App fetches `https://jeplay.ru/version.txt` (first non-comment line = remote version).
2. If newer than the installed build, it downloads
   `https://jeplay.ru/download/JePlay-<version>.apk`.
3. Optional: publish `https://jeplay.ru/download/SHA256SUMS.txt`; if missing, install
   proceeds without checksum verification.

Server layout example:

```text
jeplay.ru/version.txt          →  0.2.21.33
jeplay.ru/download/JePlay-0.2.21.33.apk
jeplay.ru/download/SHA256SUMS.txt   (optional)
```

## Website (`jaecoo/site/`)

Static HTML for [jeplay.ru](https://jeplay.ru):

| File | Role |
|------|------|
| `index.html` | What JePlay is, features, FAQ, DiPlay credit + GitHub link |
| `install.html` | Flag USB, ENCRYPTION password (v1/v2), WebUSB Connect → Install |
| `assets/install.js` | Source (WebADB). Do not load this from the page |
| `assets/install.bundle.js` | **Shipped** self-contained bundle — no CDN (esm.sh is often blocked) |
| `assets/localinstall.apk` | Helper: PackageInstaller.Session (bypasses Chery `Restriction prevents installing`) |
| `assets/svengmode.flag` | Copy to USB root to unlock engineering / DEVICE ADB |
| `download/` | Publish `JePlay-<version>.apk` here |

Chery blocks plain `adb install` / `pm install`. The web installer pushes the APK plus
`localinstall.apk` and runs `app_process … LocalInstall` (same approach as
[chery-adb-app-install](https://github.com/EvilBorsch/chery-adb-app-install)). On Windows you can
also run `jaecoo/install-via-session.cmd` next to the APK.

Rebuild the installer after editing `assets/install.js`:

```bash
cd DiPlay/jaecoo/site
npm install
npm run build
```

Local preview (WebUSB needs http, not `file://`):

```bash
cd DiPlay/jaecoo/site
python3 -m http.server 8765
# Chrome → http://localhost:8765/
```

## What actually drives the HUD

YaSwitcher (`ru.jaecoo.yanswitcher`) does **not** use Yandex ContentProviders or FCE
vehicle properties for turn arrows. It publishes Desay **CarLan VDB** events:

| Event | Bean | Role |
|------:|------|------|
| 721701 | `VDNaviDigitalInfo` | Navi status (`3` guiding, `0` idle) |
| 721708 | `VDDistNaviContent` | Distance to turn (m) |
| 721698 | `VDNaviRoadInfo` | RoadIcon + SegRemainDis + road name |
| 721697 | `VDNaviLaneInfo` | Lane glyphs (`0x5204`, selected via `0x5201` 0x10/0x12) |

Client path: bind `com.desaysv.ivi.vds.carlan` /
`action.desaysv.ivi.vds.carlan.SERVICE` → AIDL `com.desaysv.ivi.vdb.IVDBus`
transact `2` (publish). Bundle payload key is `"data"`. Service index is
`eventId >> 16` (11 = carlan).

YaSwitcher also fans out `AUTONAVI_STANDARD_BROADCAST_SEND` (`ICON`,
`SEG_REMAIN_DIS`, …); DiPlay does the same as a fallback.

RoadIcon / AUTONAVI `ICON` values follow the **YaSwitcher Desay table**, not BYD
Gaode (`BydManeuverCodes`): `left`→2, `right`→3, `straight`→9, `finish`→15
(see `DesayManeuverCodes`). Using BYD codes swaps left/right on Jaecoo HUD.

### CarLan vs Media: how `DesayVdbClient` must publish (do not break again)

`DesayVdbClient` is shared by **HUD (CarLan)** and **home-widget now-playing (Media)**.
Those two Desay services need **different** `IVDBus.set` shapes. Mixing them silently
kills HUD arrows while settings toggles and lane/road probes still look fine.

| Service | Package / action | Correct publish | Why |
|---------|------------------|-----------------|-----|
| **CarLan (HUD)** | `com.desaysv.ivi.vds.carlan` | `DesayVdbClient()` — **real event id** (721701 / 721708 / 721698 / 721697), Parcelable bean in `"data"` | Same as YaSwitcher (`R0.a` → `set(event)`). OEM `VDBinder.set` takes the real id → `onSet`. |
| **Media (widget)** | `com.desaysv.ivi.vds.media` | `DesayVdbClient.media()` — wrap as `id = MEDIA_MIN` (393216) + `InjectEventID = realId` | Plain `set(realId)` is rejected ("can not be setting"); inject **publishes** to subscribers. |

**Regression (2026-10-30 / JePlay ~0.2.21.25–31):** while wiring Media, CarLan’s default
`serviceMinEventId` was set to `CARLAN_MIN` (720896), so every HUD turn went through the
inject envelope. OEM `VDBinder.set` reads `InjectEventID` with `Bundle.getInt` **before**
`setClassLoader`. CarLan payloads carry custom Parcelables (`VDNaviRoadInfo`, …) → ClassNotFound
under BootClassLoader → inject aborts → **no arrows on HUD**, app thinks publish succeeded.
Fixed in **0.2.21.32**: CarLan default is `serviceMinEventId = null` again; only
`DesayVdbClient.media()` passes `MEDIA_MIN`.

**Lane strip regression (JePlay ≤0.2.21.32):** iAP2 repeats `0x5204` LaneGuidanceInfo
and LaneAngle as sibling TLVs with the same ID. The parser kept only the last of each,
so HUD showed a single rightmost lane (often with one angle). Fixed in **0.2.21.33**.

**Rules when touching this code:**

1. Never default CarLan to inject. HUD must send the real event id.
2. Never put CarLan Parcelable beans on the Media inject path (and do not “unify” both
   services onto one wrap mode).
3. After a `DesayVdbClient` change, verify in the car: Settings → HUD lane/road probe
   **and** live CarPlay navigation both show on the HUD/cluster.
4. Logs: `DiPlay-DesayVdb` (bind / publish failures), `DiPlay-Jaecoo` (guidance sent).
   Probe alone is not enough if CarPlay frame wiring also changed.

Code: `jaecoo/src/shared/.../hud/DesayVdbClient.kt`, `DesayHudOutput.kt`,
`media/DesayMediaOutput.kt`.

## What this overlay does

1. **HUD** — CarPlay `CPManeuver` / iAP2 `0x5201`+`0x5202` → Desay CarLan VDB
   (`DesayVdbClient` / `DesayHudOutput`). Toggle: settings → Advanced → cluster map card.
2. **Built-in Jaecoo SoftAP** — Connection setup mode that starts the head-unit SoftAP
   like DriveLabs wifihelper / OEM Desay CarPlay: broadcast
   `com.desay.systemui.set_ap_status` + reflective `setWifiApEnabled` (no hotspot UI,
   no network ADB). Prefers `wlan1`, 5 GHz / channel 149. SoftAP keeps factory credentials
   (often `HS00…`). Setup detects the SoftAP name first, then asks for that network’s
   password from the iPhone (Wi‑Fi → ⓘ → Password).
   Code: `JaecooSoftApStarter.kt`, `JaecooSoftApCredentials.kt`.
3. **Dashboard** — CarPlay instrument-cluster scene (stream 111) on a presentation
   display (unchanged).

Lane glyphs: CarPlay `0x5204` → event 721697 (`VDNaviLaneInfo`), angles mapped through
the YaSwitcher Gaode-style icon table (`DesayLaneIcons`). Requires Identify to advertise
`MaxLaneGuidanceStorageCapacity` (param 8) and list `0x5204` in MessagesReceivedFromDevice —
otherwise iOS never sends lane frames.

## Hooks in DiPlay (search `JAECOO`)

1. `shared/build.gradle` — extra source sets
2. `common/build.gradle.kts` — extra source sets
3. `common` / `shared` `AndroidManifest.xml` — CarLan package queries
4. `BydNavigationOutputs.kt` — start / clear / frames
5. `FceClusterBridge.kt` — `ownsFce()` is always false now (Desay path)
6. `ClusterMapPresentation.findDisplay` — Jaecoo display fallback
7. `DiPlayActivity` — HUD switch + Built-in Jaecoo connection mode + update checksums
8. `values/strings.xml` and locale `values-*/strings.xml` (+ update copy)
9. `AirPlayPersistence.DEFAULT_OEM_LABEL` → `Jaecoo`; manufacturer/model → `JePlay`; `res/raw/ic_car_home.png`
10. `mobile/build.gradle.kts` — `ru.jeplay.app`, `0.2.19.x`, APK `archivesName`
11. `update/*` — jeplay.ru `version.txt` + `download/` instead of GitHub releases
12. Fresh-install defaults from `jaecoo/assets/diplay-settings-defaults.json` (cluster map/music, FPS 55, `JAECOO_BUILTIN`, …)
13. `WirelessHotspotMode.JAECOO_BUILTIN` + `CarPlayController` SoftAP start via `JaecooSoftApStarter`
14. Desay audio tags: nav = usage guidance + legacy stream **62** (OEM does **not** set `key_source_id`; policy derives it — injecting 38 sent guidance to cabin). Media = `USAGE_MEDIA` + stream **60**. Nav tracks prefer `BUS02_NAV_GUIDANCE` (headrest). Settings channel numbers are ignored. Fresh defaults: audio focus **on**, media-key focus **off**. MediaSession publishes title/artist/**artwork**. iPhone `duckAudio`/`unduckAudio` attenuates media

## In-car audio routing check (nav → headrest)

1. Settings → Audio → play the navigation preview (Automatic / channel 0) — should come from the **driver headrest**.
2. Connect CarPlay, play music, start turn-by-turn: guidance in headrest, music in cabin; music should duck on each prompt.
3. Export a diagnostic report and confirm lines like:
   - `Audio: ready … audioType=default … route=usage usage=12 … routedDeviceType=… routedDeviceAddress=…`
   - `Audio: ready … audioType=media … route=usage … routedDeviceAddress=…` (address should differ from nav when policy splits buses)
   - `Audio: session duck volume=…` / `Audio: session unduck`
4. If nav still plays in the cabin, capture `dumpsys audio` while OEM CarPlay speaks a prompt and compare `key_source_id` / devices.
