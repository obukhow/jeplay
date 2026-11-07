# ADB broadcasts from Chrome

`adb-broadcasts.html` — страница на WebUSB/WebADB: подключение к ГУ из Chrome и дамп broadcast’ов / providers / packages / logcat по DriveLabs.

## Запуск

WebUSB не работает с `file://`. Поднимите локальный сервер из этой папки:

```bash
cd DiPlay/jaecoo/tools
python3 -m http.server 8765
```

Откройте в **Chrome**: http://localhost:8765/adb-broadcasts.html

На авто: USB debugging, подтвердите RSA-ключ при первом подключении.

## Что умеет

- `dumpsys activity broadcasts` — недавние Intent’ы (фильтр `drivelabs` / `navi_started` / …)
- `dumpsys package providers` — кто держит `pro.drivelabs*`
- `pm list packages` — пакеты drive/navi/yandex
- `logcat -d` / watch 20 с — следы broadcast’ов в логе
- `query-receivers` на `pro.drivelabs.navi_started`

Живой `BroadcastReceiver` через ADB поставить нельзя без своего APK: только история и logcat.
