/**
 * Heuristic analysis of JePlay / DiPlay connection logs (diplay.log / diagnostic reports).
 * Returns { code, title, detail, steps[] } for the installer UI.
 */

/**
 * @typedef {{ code: string, title: string, detail: string, steps: string[] }} DiagnoseResult
 */

/** @param {string} text @param {"ru"|"en"} lang */
export function analyzeConnectionLogs(text, lang = "ru") {
  const raw = String(text || "");
  const log = raw.toLowerCase();
  const rules = RULES.map((rule) => ({
    ...rule,
    hit: rule.test(raw, log),
  })).filter((r) => r.hit);

  if (!raw.trim()) {
    return localize(
      {
        code: "empty",
        titleRu: "Logcat пустой",
        titleEn: "Logcat is empty",
        detailRu:
          "По ADB не видно свежих строк JePlay. Откройте JePlay, попробуйте подключиться, пока идёт сбор, затем завершите анализ.",
        detailEn:
          "No recent JePlay lines over ADB. Open JePlay, try connecting while collection runs, then finish analysis.",
        stepsRu: [
          "Откройте JePlay на ГУ",
          "Запустите диагностику и сразу попробуйте wireless",
          "Нажмите «Завершить анализ»",
        ],
        stepsEn: [
          "Open JePlay on the head unit",
          "Start diagnostics and immediately try wireless",
          "Press “Finish analysis”",
        ],
      },
      lang,
    );
  }

  if (!rules.length) {
    const hasWireless = /phase=WIRELESS|wireless bring-up|HotspotReady|ConnectingBluetooth/i.test(raw);
    const looksOk = /WirelessActive|RunningWireless|wireless carplay active/i.test(raw);
    if (looksOk) {
      return localize(
        {
          code: "ok",
          titleRu: "По логам сессия выглядела рабочей",
          titleEn: "Logs look like a working session",
          detailRu: "В логах есть признаки активного wireless CarPlay. Если сейчас чёрный экран — перезапустите JePlay и пришлите свежий отчёт.",
          detailEn: "Logs show signs of an active wireless CarPlay session. If the screen is black now, restart JePlay and send a fresh report.",
          stepsRu: ["Перезапустите JePlay", "Если снова сбой — отправьте логи разработчику"],
          stepsEn: ["Restart JePlay", "If it fails again — send the logs to the developer"],
        },
        lang,
      );
    }
    return localize(
      {
        code: "unknown",
        titleRu: hasWireless ? "Точная причина по шаблонам не найдена" : "В логах мало данных о wireless",
        titleEn: hasWireless ? "No matching failure pattern" : "Little wireless data in the logs",
        detailRu: "Автоматический разбор не нашёл известный сбой. Отправьте логи разработчику вместе с Remote debug ID.",
        detailEn: "Automatic analysis found no known failure. Send the logs to the developer with your Remote debug ID.",
        stepsRu: [
          "Нажмите «Отправить логи разработчику»",
          "Передайте в поддержку Remote debug ID со страницы установки",
        ],
        stepsEn: [
          "Press “Send logs to developer”",
          "Give support the Remote debug ID from the install page",
        ],
      },
      lang,
    );
  }

  // Highest priority first (rules are ordered).
  const best = rules[0];
  return localize(best, lang);
}

/** @param {object} rule @param {"ru"|"en"} lang @returns {DiagnoseResult} */
function localize(rule, lang) {
  const en = lang === "en";
  return {
    code: rule.code,
    title: en ? rule.titleEn : rule.titleRu,
    detail: en ? rule.detailEn : rule.detailRu,
    steps: en ? rule.stepsEn : rule.stepsRu,
  };
}

const RULES = [
  {
    code: "softap_password",
    test: (raw, log) =>
      /JAECOO_SOFTAP_PASSWORD_REQUIRED/i.test(raw) ||
      (/settings_jaecoo_need_password|need the password for|укажите пароль SoftAP/i.test(raw) &&
        /HOTSPOT_CONFIGURATION/i.test(raw)) ||
      (/passphraseLen=0|empty passphrase|manualHotspotPassphrase/i.test(log) &&
        /jaecoo|softap|JAECOO_BUILTIN/i.test(raw)),
    titleRu: "Не указан пароль SoftAP",
    titleEn: "SoftAP password is missing",
    detailRu:
      "JePlay поднял точку доступа машины, но пароль сети не сохранён. Без него iPhone не подключится к Wi‑Fi автомобиля.",
    detailEn:
      "JePlay brought up the car SoftAP, but the Wi‑Fi password is not saved. Without it the iPhone cannot join the car hotspot.",
    stepsRu: [
      "В JePlay → Подключение → встроенный SoftAP сохраните пароль (iPhone: Wi‑Fi → ⓘ у сети HS… → Пароль)",
      "Или на странице установки: Утилиты → «Перечитать пароль Hotspot»",
      "Повторите подключение",
    ],
    stepsEn: [
      "In JePlay → Connection → built-in SoftAP, save the password (iPhone: Wi‑Fi → ⓘ on HS… → Password)",
      "Or on the install page: Utilities → “Re-read Hotspot password”",
      "Try connecting again",
    ],
  },
  {
    code: "iphone_unpaired",
    test: (raw) => /no longer paired|Choose it again in DiPlay|выбранный iPhone больше не сопряжён/i.test(raw),
    titleRu: "iPhone не сопряжён по Bluetooth",
    titleEn: "iPhone is not Bluetooth-paired",
    detailRu:
      "JePlay не видит выбранный iPhone в списке сопряжённых устройств Bluetooth головного устройства.",
    detailEn:
      "JePlay cannot see the selected iPhone among the head unit’s paired Bluetooth devices.",
    stepsRu: [
      "На ГУ: Bluetooth → забыть iPhone",
      "На iPhone: забыть эту машину (и запись CarPlay, если есть)",
      "Спарьте заново, в JePlay снова выберите этот телефон",
    ],
    stepsEn: [
      "On the head unit: Bluetooth → forget the iPhone",
      "On the iPhone: forget this car (and any CarPlay entry)",
      "Pair again, then re-select the phone in JePlay",
    ],
  },
  {
    code: "bt_iap2_rfcomm",
    test: (raw, log) =>
      (/Bluetooth connect failed|BluetoothSocket\.readAll|read failed, socket might closed/i.test(raw) &&
        /iAP2|RFCOMM|ConnectingBluetooth|waitingFor=Bluetooth_iAP2/i.test(raw)) ||
      (/waitingFor=Bluetooth_iAP2_authentication/i.test(raw) &&
        /authenticated=false/i.test(log) &&
        /wifiConfigs=0/i.test(log) &&
        /tcpAccepted=0/i.test(log)),
    titleRu: "Сбой Bluetooth iAP2 (RFCOMM)",
    titleEn: "Bluetooth iAP2 (RFCOMM) failure",
    detailRu:
      "Точка SoftAP уже готова, но JePlay не может открыть служебный канал CarPlay по Bluetooth. До передачи Wi‑Fi-пароля на iPhone дело не доходит. Часто мешает штатный CarPlay или «залипшая» BT-пара.",
    detailEn:
      "SoftAP is ready, but JePlay cannot open the CarPlay Bluetooth control channel. Wi‑Fi credentials never reach the iPhone. Built-in CarPlay or a stuck BT pairing often causes this.",
    stepsRu: [
      "Утилиты → выключите системный CarPlay (и TurboDog, если есть)",
      "Забудьте iPhone на ГУ и на телефоне, спарьте заново",
      "При запросе CarPlay на ГУ откажитесь; в JePlay выберите iPhone и примите CarPlay на телефоне",
      "Если порт 7000 занят штатным стеком — перезагрузите ГУ после отключения OEM CarPlay",
    ],
    stepsEn: [
      "Utilities → disable system CarPlay (and TurboDog if present)",
      "Forget the iPhone on the head unit and phone, then pair again",
      "Decline CarPlay on the head unit prompt; in JePlay select the iPhone and accept CarPlay on the phone",
      "If port 7000 is held by the OEM stack — reboot the head unit after disabling OEM CarPlay",
    ],
  },
  {
    code: "port_7000_busy",
    test: (raw) => /preferred 7000 in use|port=7001 \(preferred 7000/i.test(raw),
    titleRu: "Порт AirPlay 7000 занят",
    titleEn: "AirPlay port 7000 is busy",
    detailRu:
      "JePlay слушает AirPlay на 7001, потому что 7000 уже занят (часто штатным CarPlay). Это может мешать обнаружению сервиса iPhone.",
    detailEn:
      "JePlay is listening on AirPlay port 7001 because 7000 is already taken (often by OEM CarPlay). That can break iPhone discovery.",
    stepsRu: [
      "Утилиты → выключите системный CarPlay",
      "Перезапустите JePlay (лучше перезагрузить ГУ)",
      "Повторите подключение",
    ],
    stepsEn: [
      "Utilities → disable system CarPlay",
      "Restart JePlay (rebooting the head unit is better)",
      "Try connecting again",
    ],
  },
  {
    code: "first_tcp_timeout",
    test: (raw) =>
      /FIRST_TCP_TIMEOUT|firstTcpAfterStartMs=|first tcp timeout/i.test(raw) ||
      (/authenticated=true/i.test(raw) &&
        /tcpAccepted=0/i.test(raw) &&
        /wifiConfigs=[1-9]/i.test(raw) &&
        /Failed|timeout/i.test(raw)),
    titleRu: "iPhone не пришёл по Wi‑Fi (TCP/AirPlay)",
    titleEn: "iPhone never arrived over Wi‑Fi (TCP/AirPlay)",
    detailRu:
      "Bluetooth-аутентификация или выдача Wi‑Fi-профиля прошли, но TCP/AirPlay-сессия так и не открылась. Обычно iPhone не в той сети SoftAP или Auto-Join выключен.",
    detailEn:
      "Bluetooth auth or Wi‑Fi profile delivery happened, but no TCP/AirPlay session opened. Usually the iPhone is not on the SoftAP network or Auto-Join is off.",
    stepsRu: [
      "На iPhone вручную подключитесь к сети SoftAP машины (HS…) и включите «Автостыковка»",
      "Проверьте пароль SoftAP в JePlay",
      "Отключите VPN / частный адрес Wi‑Fi на время проверки",
    ],
    stepsEn: [
      "On the iPhone, join the car SoftAP (HS…) manually and enable Auto-Join",
      "Verify the SoftAP password in JePlay",
      "Turn off VPN / Private Wi‑Fi Address while testing",
    ],
  },
  {
    code: "hotspot_not_ready",
    test: (raw) =>
      /HOTSPOT_NOT_READY|hotspot_network_not_ready|Hotspot network is not ready|StartingHotspot.*Failed/i.test(
        raw,
      ),
    titleRu: "SoftAP / hotspot не готов",
    titleEn: "SoftAP / hotspot is not ready",
    detailRu: "JePlay не смог подтвердить готовую точку доступа на интерфейсе машины.",
    detailEn: "JePlay could not confirm a ready hotspot interface on the head unit.",
    stepsRu: [
      "Убедитесь, что выбран режим «встроенный SoftAP Jaecoo»",
      "Включите Wi‑Fi / SoftAP на ГУ, повторите",
      "Утилиты → перечитайте пароль Hotspot",
    ],
    stepsEn: [
      "Make sure “built-in Jaecoo SoftAP” mode is selected",
      "Enable Wi‑Fi / SoftAP on the head unit, then retry",
      "Utilities → re-read Hotspot password",
    ],
  },
  {
    code: "oem_carplay_present",
    test: (raw) =>
      /com\.desaysv\.vehicle\.carplayapp/i.test(raw) &&
      /enabled|Running|conflict|7000 in use/i.test(raw),
    titleRu: "Возможен конфликт со штатным CarPlay",
    titleEn: "Possible conflict with OEM CarPlay",
    detailRu: "В логах фигурирует штатный CarPlay Desay — он может перехватывать Bluetooth/AirPlay.",
    detailEn: "Logs mention Desay OEM CarPlay — it may capture Bluetooth/AirPlay.",
    stepsRu: ["Утилиты → выключите системный CarPlay", "Перезапустите подключение JePlay"],
    stepsEn: ["Utilities → disable system CarPlay", "Restart the JePlay connection"],
  },
];
