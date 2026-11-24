/**
 * JePlay browser installer: WebUSB ADB + local ENCRYPTION password calc.
 * Opens Chrome device picker on Connect; after auth the same button becomes Install.
 */
// Bundled into install.bundle.js — no CDN at runtime (esm.sh often blocked on Windows).
import { Adb, AdbDaemonTransport } from "@yume-chan/adb";
import { AdbDaemonWebUsbDeviceManager } from "@yume-chan/adb-daemon-webusb";
import AdbWebCredentialStore from "@yume-chan/adb-credential-web";
import { analyzeConnectionLogs } from "./connection-diagnose.js";

const PACKAGE_ID = "ru.jeplay.app";
const VERSION_URL = new URL("../version.txt", import.meta.url).href;
const DOWNLOAD_BASE = new URL("../download/", import.meta.url).href;
const HELPER_URL = new URL("./localinstall.apk", import.meta.url).href;
const REMOTE_TARGET = "/data/local/tmp/jeplay-install-target.apk";
const REMOTE_HELPER = "/data/local/tmp/jeplay-localinstall.apk";
const SOFTAP_IMPORT_PATH = "/data/local/tmp/ru.jeplay.app-softap-import";
const APCONFIG_READ_HEADER = "DIPLAY_HOTSPOT_APCONFIG_READ_V1|";
const HOTSPOT_HELPER_CLASS = "com.shilapi.xcertplay.network.CarHotspotStartMain";
const CREDENTIAL_STORE_NAME = "jeplay-webadb";

/** OEM packages toggled from the Utilities popup / post-install. */
const OEM_CARPLAY_PKG = "com.desaysv.vehicle.carplayapp";
const DRIVELABS_NAVI_PKG = "ru.yandex.yandexnavi.auto";
const YANDEX_MAPS_PKGS = ["ru.yandex.yandexmaps", "ru.yandex.yandexmaps.app"];
const TURBODOG_PKG = "com.astrob.turbodog";

/** ENCRYPTION v2 multiplier (password = (K * productId * rand) % 1e6). */
const V2_K = 566789n;

const RUNTIME_GRANTS = [
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.RECORD_AUDIO",
  "android.permission.BLUETOOTH_CONNECT",
  "android.permission.BLUETOOTH_SCAN",
  "android.permission.NEARBY_WIFI_DEVICES",
  "android.permission.POST_NOTIFICATIONS",
  "android.permission.BLUETOOTH",
  "android.permission.BLUETOOTH_ADMIN",
];

const APPOPS = [
  "SYSTEM_ALERT_WINDOW",
  "GET_USAGE_STATS",
  "WRITE_SETTINGS",
  "ACTIVATE_VPN",
  "REQUEST_INSTALL_PACKAGES",
  "MANAGE_EXTERNAL_STORAGE",
  "LEGACY_STORAGE",
  "SCHEDULE_EXACT_ALARM",
  "ACCESS_NOTIFICATIONS",
];

const $ = (id) => document.getElementById(id);

/** @param {string} target @param {Record<string, unknown>} [params] */
function trackGoal(target, params) {
  if (typeof window.jeplayYmGoal === "function") {
    window.jeplayYmGoal(target, params);
  }
}

const LANG = (document.documentElement.lang || "ru").toLowerCase().startsWith("en") ? "en" : "ru";

const STRINGS = {
  ru: {
    waitingConnect: "Ожидание подключения…",
    updateOver: "Обновить поверх",
    install: "Установить",
    installed: "установлен",
    checkingInstalled: "Проверяю, установлен ли JePlay…",
    notInstalled: "JePlay на ГУ не найден — можно ставить с нуля.",
    alreadyInstalled: "JePlay уже установлен: {label}",
    uninstalling: "Удаляю {id}…",
    emptyUninstallUser0: "(пустой ответ pm uninstall --user 0)",
    emptyUninstall: "(пустой ответ pm uninstall)",
    uninstallFailed: "Не удалось удалить JePlay по ADB. Удалите вручную: Настройки → Приложения → JePlay → Удалить, затем повторите установку.",
    stillInstalled: "После uninstall пакет всё ещё на месте",
    uninstalled: "JePlay удалён",
    enterData: "Введите данные выше",
    copy: "Копировать",
    copied: "Скопировано",
    selectManually: "Выделите вручную",
    webusbUnavailable: "WebUSB недоступен. Откройте эту страницу в Chrome по https:// или http://localhost",
    needSecureContext: "Нужен secure context: https:// или http://localhost (не file://)",
    pickDevice: "Выберите устройство…",
    openingPicker: "Открываю окно выбора USB-устройства Chrome…",
    deviceNotSelected: "Устройство не выбрано",
    connecting: "Подключение…",
    deviceLabel: "Устройство: {serial}",
    noSerial: "(без serial)",
    confirmRsa: "Если ГУ спросит RSA — подтвердите доступ",
    connected: "Подключено: {serial}",
    adbReady: "ADB готов.",
    chooseReinstallOrUpdate: "Выберите: удалить и поставить заново, или обновить поверх.",
    pressInstall: "Нажмите «Установить».",
    disconnected: "Отключено",
    readingVersion: "Читаю {url}…",
    badVersion: "В version.txt нет распознаваемой версии",
    siteVersion: "Версия на сайте: {version}",
    downloading: "Скачиваю {url}…",
    apkTooSmall: "Скачанный APK слишком маленький — проверьте файл на сервере",
    apkSize: "APK {name}: {mb} МБ",
    uploading: "Загрузка → {path} ({kb} КБ)",
    helperMissing: "Не найден helper localinstall.apk на сайте (HTTP {status})",
    tryingPmInstall: "Пробую pm install…",
    emptyPmInstall: "(пустой ответ pm install)",
    shellSocketFallback: "ADB отклонил команду (Socket open failed) — запускаю через временный sh-скрипт…",
    firmwareBlocks: "Прошивка блокирует pm install — ставлю через PackageInstaller.Session…",
    pmFailedTrySession: "pm install не сработал — пробую PackageInstaller.Session…",
    emptyLocalInstall: "(пустой ответ LocalInstall)",
    confirmOnCar: "На экране автомобиля нужно подтвердить установку (диалог Install). Подтвердите и повторите.",
    installFailed: "Установка не удалась (Restriction / PackageInstaller).",
    grantUnknownApps: "Разрешаю установку обновлений из JePlay (unknown apps)…",
    notConfirmed: "не подтверждено",
    unknownAppsHint: "  Обновление кнопкой в JePlay может снова спросить Settings — откройте APK из Downloads.",
    grantingRuntime: "Выдаю runtime-разрешения…",
    grantingAppops: "Выдаю appops…",
    openNetwork: "(открытая сеть)",
    readingSoftAp: "Читаю SoftAP (SSID/пароль) через ADB…",
    softApUnread: "SoftAP не прочитан ({error}). Пароль можно ввести в JePlay с iPhone.",
    noResponse: "нет ответа",
    softApWritten: "Записал настройки SoftAP → {path} (JePlay подхватит при запуске)",
    connectFirst: "Сначала подключите устройство",
    reinstalling: "Переустановка…",
    installing: "Установка…",
    nothingToUninstall: "Удалять нечего — JePlay уже отсутствует.",
    packageMissing: "(пакет не найден после установки)",
    packageNotFound: "Пакет ru.jeplay.app не найден после установки",
    done: "Готово",
    softApOk: " SoftAP «{ssid}» записан в JePlay — откройте приложение на ГУ.",
    softApFail: " SoftAP с ГУ не удалось прочитать — пароль введите в JePlay с iPhone.",
    reinstalled: "переустановлен",
    installedWord: "установлен",
    success: "Успех: JePlay {version}",
    installError: "Ошибка установки: {message}",
    openHttps: "Откройте через http://localhost или https://",
    fileProtocol: "WebUSB не работает с file:// — поднимите локальный сервер или откройте сайт по HTTPS.",
    debugSession: "Remote debug ID: {id}",
    debugCmd: "Remote debug → {cmd}",
    debugFailed: "Remote debug недоступен: {error}",
    utilities: "Утилиты",
    utilDisableCarplay: "Выключить системный CarPlay",
    utilEnableCarplay: "Включить системный CarPlay",
    utilDisableDrivelabs: "Выключить навигатор DriveLabs",
    utilEnableDrivelabs: "Включить навигатор DriveLabs",
    utilDisableMaps: "Выключить Яндекс.Карты",
    utilEnableMaps: "Включить Яндекс.Карты",
    utilDisableTurbodog: "Выключить TurboDog",
    utilEnableTurbodog: "Включить TurboDog",
    utilDisablingTurbodog: "Отключаю TurboDog ({id})…",
    utilTurbodogDisabled: "TurboDog отключён",
    utilTurbodogSkip: "TurboDog ({id}) не найден или уже выключен — пропускаю",
    utilPkgMissing: "Пакет {id} на ГУ не найден",
    utilPkgEnabled: "{label}: включён",
    utilPkgDisabled: "{label}: выключен",
    utilToggling: "{action}…",
    utilToggleOk: "Готово: {id} → {state}",
    utilToggleFail: "Не удалось изменить {id}: {error}",
    utilStateOn: "включён",
    utilStateOff: "выключен",
    utilDisablingOem: "Отключаю системный CarPlay ({id})…",
    utilEnablingOem: "Включаю системный CarPlay ({id})…",
    utilOemDisabled: "Системный CarPlay отключён",
    utilOemEnabled: "Системный CarPlay снова включён",
    utilOemSkip: "Системный CarPlay ({id}) не найден — пропускаю",
    utilReadingHotspot: "Читаю SoftAP заново…",
    utilHotspotOk: "Hotspot: {ssid} / {pass}",
    utilHotspotFail: "Не удалось прочитать SoftAP ({error})",
    utilReportCollect: "Собираю диагностический отчёт с ГУ…",
    utilReportUpload: "Отправляю отчёт на сервер…",
    utilReportOk: "Отчёт отправлен разработчику (id {id}, {kb} КБ)",
    utilReportFail: "Не удалось отправить отчёт: {error}",
    utilReportEmpty: "В logcat нет свежих строк JePlay. Попробуйте подключиться в приложении и повторите.",
    utilNeedConnect: "Сначала подключите устройство",
    utilDiag: "Диагностика подключения",
    utilDiagCollecting: "Слушаю logcat… Сейчас попробуйте подключиться в JePlay на ГУ.",
    utilDiagCollectTick: "Сбор logcat… ({n} фрагм., {kb} КБ)",
    utilDiagFinish: "Завершить анализ",
    utilDiagSend: "Отправить логи разработчику",
    utilDiagAnalyzing: "Разбираю logcat…",
    utilDiagNoJeplay: "JePlay на ГУ не найден — сначала установите приложение и попробуйте подключиться.",
    utilDiagDoneTitle: "Результат",
    utilDiagSendOk: "Логи сохранены на сервере. Передайте разработчику код: {code}",
    utilDiagSendNeedSession: "Нет Remote debug ID — логи всё равно загружены (id {id}). Лучше переподключитесь, чтобы был ID сессии.",
    utilDiagReason: "Вероятная причина",
    utilDiagWhatToDo: "Что сделать",
  },
  en: {
    waitingConnect: "Waiting for connection…",
    updateOver: "Update in place",
    install: "Install",
    installed: "installed",
    checkingInstalled: "Checking whether JePlay is installed…",
    notInstalled: "JePlay not found on the head unit — ready for a fresh install.",
    alreadyInstalled: "JePlay already installed: {label}",
    uninstalling: "Uninstalling {id}…",
    emptyUninstallUser0: "(empty pm uninstall --user 0 response)",
    emptyUninstall: "(empty pm uninstall response)",
    uninstallFailed: "Could not uninstall JePlay over ADB. Remove it manually: Settings → Apps → JePlay → Uninstall, then try again.",
    stillInstalled: "Package is still present after uninstall",
    uninstalled: "JePlay uninstalled",
    enterData: "Enter the values above",
    copy: "Copy",
    copied: "Copied",
    selectManually: "Select manually",
    webusbUnavailable: "WebUSB is unavailable. Open this page in Chrome over https:// or http://localhost",
    needSecureContext: "Secure context required: https:// or http://localhost (not file://)",
    pickDevice: "Choose a device…",
    openingPicker: "Opening Chrome USB device picker…",
    deviceNotSelected: "No device selected",
    connecting: "Connecting…",
    deviceLabel: "Device: {serial}",
    noSerial: "(no serial)",
    confirmRsa: "If the head unit asks for an RSA key — confirm access",
    connected: "Connected: {serial}",
    adbReady: "ADB is ready.",
    chooseReinstallOrUpdate: "Choose: uninstall and reinstall, or update in place.",
    pressInstall: "Press Install.",
    disconnected: "Disconnected",
    readingVersion: "Reading {url}…",
    badVersion: "No recognizable version in version.txt",
    siteVersion: "Site version: {version}",
    downloading: "Downloading {url}…",
    apkTooSmall: "Downloaded APK is too small — check the file on the server",
    apkSize: "APK {name}: {mb} MB",
    uploading: "Uploading → {path} ({kb} KB)",
    helperMissing: "Helper localinstall.apk not found on the site (HTTP {status})",
    tryingPmInstall: "Trying pm install…",
    emptyPmInstall: "(empty pm install response)",
    shellSocketFallback: "ADB rejected the command (Socket open failed) — retrying via a temporary sh script…",
    firmwareBlocks: "Firmware blocks pm install — installing via PackageInstaller.Session…",
    pmFailedTrySession: "pm install failed — trying PackageInstaller.Session…",
    emptyLocalInstall: "(empty LocalInstall response)",
    confirmOnCar: "Confirm the install dialog on the car screen, then try again.",
    installFailed: "Install failed (Restriction / PackageInstaller).",
    grantUnknownApps: "Allowing JePlay to install updates (unknown apps)…",
    notConfirmed: "not confirmed",
    unknownAppsHint: "  In-app update may still open Settings — open the APK from Downloads.",
    grantingRuntime: "Granting runtime permissions…",
    grantingAppops: "Granting appops…",
    openNetwork: "(open network)",
    readingSoftAp: "Reading SoftAP (SSID/password) over ADB…",
    softApUnread: "SoftAP not read ({error}). You can enter the password in JePlay from the iPhone.",
    noResponse: "no response",
    softApWritten: "Wrote SoftAP settings → {path} (JePlay will pick them up on launch)",
    connectFirst: "Connect a device first",
    reinstalling: "Reinstalling…",
    installing: "Installing…",
    nothingToUninstall: "Nothing to uninstall — JePlay is already absent.",
    packageMissing: "(package not found after install)",
    packageNotFound: "Package ru.jeplay.app not found after install",
    done: "Done",
    softApOk: " SoftAP “{ssid}” saved into JePlay — open the app on the head unit.",
    softApFail: " Could not read SoftAP from the head unit — enter the password in JePlay from the iPhone.",
    reinstalled: "reinstalled",
    installedWord: "installed",
    success: "Success: JePlay {version}",
    installError: "Install error: {message}",
    openHttps: "Open via http://localhost or https://",
    fileProtocol: "WebUSB does not work with file:// — run a local server or open the site over HTTPS.",
    debugSession: "Remote debug ID: {id}",
    debugCmd: "Remote debug → {cmd}",
    debugFailed: "Remote debug unavailable: {error}",
    utilities: "Utilities",
    utilDisableCarplay: "Disable system CarPlay",
    utilEnableCarplay: "Enable system CarPlay",
    utilDisableDrivelabs: "Disable DriveLabs navigator",
    utilEnableDrivelabs: "Enable DriveLabs navigator",
    utilDisableMaps: "Disable Yandex Maps",
    utilEnableMaps: "Enable Yandex Maps",
    utilDisableTurbodog: "Disable TurboDog",
    utilEnableTurbodog: "Enable TurboDog",
    utilDisablingTurbodog: "Disabling TurboDog ({id})…",
    utilTurbodogDisabled: "TurboDog disabled",
    utilTurbodogSkip: "TurboDog ({id}) not found or already disabled — skipped",
    utilPkgMissing: "Package {id} not found on the head unit",
    utilPkgEnabled: "{label}: enabled",
    utilPkgDisabled: "{label}: disabled",
    utilToggling: "{action}…",
    utilToggleOk: "Done: {id} → {state}",
    utilToggleFail: "Could not change {id}: {error}",
    utilStateOn: "enabled",
    utilStateOff: "disabled",
    utilDisablingOem: "Disabling system CarPlay ({id})…",
    utilEnablingOem: "Enabling system CarPlay ({id})…",
    utilOemDisabled: "System CarPlay disabled",
    utilOemEnabled: "System CarPlay re-enabled",
    utilOemSkip: "System CarPlay ({id}) not found — skipped",
    utilReadingHotspot: "Re-reading SoftAP…",
    utilHotspotOk: "Hotspot: {ssid} / {pass}",
    utilHotspotFail: "Could not read SoftAP ({error})",
    utilReportCollect: "Collecting diagnostic report from the head unit…",
    utilReportUpload: "Uploading report to the server…",
    utilReportOk: "Report sent to developer (id {id}, {kb} KB)",
    utilReportFail: "Could not send report: {error}",
    utilReportEmpty: "No recent JePlay lines in logcat. Try connecting in the app, then retry.",
    utilNeedConnect: "Connect a device first",
    utilDiag: "Connection diagnostics",
    utilDiagCollecting: "Listening to logcat… Try connecting in JePlay on the head unit now.",
    utilDiagCollectTick: "Collecting logcat… ({n} chunks, {kb} KB)",
    utilDiagFinish: "Finish analysis",
    utilDiagSend: "Send logs to developer",
    utilDiagAnalyzing: "Analyzing logcat…",
    utilDiagNoJeplay: "JePlay is not installed — install it first and try connecting.",
    utilDiagDoneTitle: "Result",
    utilDiagSendOk: "Logs saved on the server. Tell the developer this code: {code}",
    utilDiagSendNeedSession: "No Remote debug ID — logs were still uploaded (id {id}). Reconnect so a session ID is available.",
    utilDiagReason: "Likely cause",
    utilDiagWhatToDo: "What to do",
  },
};

/** @param {keyof typeof STRINGS.ru} key @param {Record<string, string | number>} [vars] */
function t(key, vars) {
  let s = STRINGS[LANG][key] ?? STRINGS.ru[key] ?? String(key);
  if (vars) {
    for (const [k, v] of Object.entries(vars)) {
      s = s.split(`{${k}}`).join(String(v));
    }
  }
  return s;
}


const actionBtn = $("action-btn");
const disconnectBtn = $("disconnect-btn");
const utilitiesBtn = $("utilities-btn");
const statusPill = $("status-pill");
const logEl = $("log");
const finalBanner = $("final-banner");
const existingPanel = $("existing-panel");
const existingVersionEl = $("existing-version");
const reinstallBtn = $("reinstall-btn");
const softapPanel = $("softap-panel");
const softapSsidEl = $("softap-ssid");
const softapPassEl = $("softap-pass");
const passwordOut = $("password-out");
const utilModal = $("util-modal");
const utilModalLog = $("util-modal-log");
const utilCarplayBtn = $("util-carplay-btn");
const utilDrivelabsBtn = $("util-drivelabs-btn");
const utilMapsBtn = $("util-maps-btn");
const utilTurbodogBtn = $("util-turbodog-btn");
const utilHotspotBtn = $("util-hotspot-btn");
const utilDiagBtn = $("util-diag-btn");
const utilDiagPanel = $("util-diag-panel");
const utilDiagProgress = $("util-diag-progress");
const utilDiagStatus = $("util-diag-status");
const utilDiagFinish = $("util-diag-finish");
const utilDiagSend = $("util-diag-send");
const utilDiagResult = $("util-diag-result");
const utilReportBtn = $("util-report-btn");

/** @type {{ timer: ReturnType<typeof setInterval> | null, chunks: string[], running: boolean, result: ReturnType<typeof analyzeConnectionLogs> | null, text: string }} */
const diagState = {
  timer: null,
  chunks: [],
  running: false,
  result: null,
  text: "",
};

let adb = null;
let connection = null;
let busy = false;
/** @type {{ path: string, versionName: string | null, versionCode: string | null } | null} */
let installedPackage = null;

/** @type {string | null} */
let debugSessionId = null;
/** @type {ReturnType<typeof setInterval> | null} */
let debugPollTimer = null;
let debugBusy = false;

function debugApiUrl(name) {
  const base = location.pathname.includes("/en/") ? "../debug/" : "debug/";
  return new URL(base + name, location.href).href;
}

async function debugPost(name, body) {
  const res = await fetch(debugApiUrl(name), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
  });
  const raw = await res.text();
  let data = {};
  try {
    data = raw ? JSON.parse(raw) : {};
  } catch {
    const snippet = raw.replace(/\s+/g, " ").trim().slice(0, 160);
    throw new Error(`debug ${name}: HTTP ${res.status} (not JSON${snippet ? `: ${snippet}` : ""})`);
  }
  if (!res.ok || data?.ok === false) {
    throw new Error(data?.error || `debug ${name}: HTTP ${res.status}`);
  }
  return data;
}

async function startRemoteDebug(serial) {
  stopRemoteDebug();
  try {
    const data = await debugPost("register.php", {
      serial: serial || "",
      lang: LANG,
      page: location.pathname,
    });
    debugSessionId = String(data.id || "");
    if (!debugSessionId) {
      log(t("debugFailed", { error: "empty id from server" }), "err");
      return;
    }
    log(t("debugSession", { id: debugSessionId }), "ok");
    debugPollTimer = setInterval(() => {
      pollRemoteDebug().catch(() => {});
    }, 2000);
    await pollRemoteDebug();
  } catch (error) {
    const message = String(error?.message || error);
    console.warn("remote debug register failed", error);
    log(t("debugFailed", { error: message }), "err");
  }
}

function stopRemoteDebug() {
  if (debugPollTimer) {
    clearInterval(debugPollTimer);
    debugPollTimer = null;
  }
  const id = debugSessionId;
  debugSessionId = null;
  if (id) {
    debugPost("offline.php", { id }).catch(() => {});
  }
}

async function pollRemoteDebug() {
  // Don't interleave remote shell with install/push — shared ADB connection.
  if (!debugSessionId || !adb || debugBusy || busy) return;
  const data = await debugPost("poll.php", { id: debugSessionId });
  const commands = Array.isArray(data.commands) ? data.commands : [];
  if (!commands.length) return;

  debugBusy = true;
  try {
    for (const item of commands) {
      const cmdId = String(item?.id || "");
      const cmd = String(item?.cmd || "").trim();
      if (!cmdId || !cmd) continue;
      log(t("debugCmd", { cmd }), "dim");
      let output = "";
      let error = null;
      try {
        output = await shell(cmd);
      } catch (e) {
        error = String(e?.message || e);
        output = error;
        log(error, "err");
      }
      await debugPost("result.php", {
        id: debugSessionId,
        cmdId,
        cmd,
        output,
        error,
      });
    }
  } finally {
    debugBusy = false;
  }
}

function setStatus(text, kind = "") {
  statusPill.textContent = text;
  statusPill.className = `status-pill${kind ? ` ${kind}` : ""}`;
}

function log(line, kind = "") {
  const span = document.createElement("span");
  if (kind) span.className = kind;
  span.textContent = `${line}\n`;
  if (logEl.textContent === t("waitingConnect") || logEl.dataset.idle === "1") {
    logEl.textContent = "";
    delete logEl.dataset.idle;
  }
  logEl.appendChild(span);
  logEl.scrollTop = logEl.scrollHeight;
}

function showFinal(ok, message) {
  finalBanner.className = `final-banner show ${ok ? "ok" : "err"}`;
  finalBanner.textContent = message;
}

function setConnectedUi(on) {
  if (!on) {
    actionBtn.textContent = "Connect";
    closeUtilitiesModal();
  } else if (installedPackage) {
    actionBtn.textContent = t("updateOver");
  } else {
    actionBtn.textContent = t("install");
  }
  actionBtn.disabled = false;
  disconnectBtn.disabled = !on;
  if (utilitiesBtn) {
    utilitiesBtn.hidden = !on;
    utilitiesBtn.disabled = !on || busy;
    utilitiesBtn.textContent = t("utilities");
  }
  if (reinstallBtn) reinstallBtn.disabled = !on || busy;
}

function hideExistingPanel() {
  installedPackage = null;
  if (existingPanel) existingPanel.hidden = true;
  if (existingVersionEl) existingVersionEl.textContent = "";
}

function showExistingPanel(info) {
  installedPackage = info;
  if (!existingPanel || !existingVersionEl) return;
  existingVersionEl.textContent = info.versionName || info.versionCode || t("installed");
  existingPanel.hidden = false;
}

/** @returns {Promise<{ path: string, versionName: string | null, versionCode: string | null } | null>} */
async function probeInstalledPackage() {
  const pathOut = await shell(`pm path ${PACKAGE_ID} 2>&1 || true`);
  const path = pathOut
    .split(/\r?\n/)
    .map((s) => s.trim())
    .find((s) => /^package:/i.test(s))
    ?.replace(/^package:/i, "")
    ?.trim();
  if (!path) return null;

  const dump = await shell(`dumpsys package ${PACKAGE_ID} 2>&1 || true`);
  const versionName = dump.match(/versionName=(\S+)/)?.[1] || null;
  const versionCode = dump.match(/versionCode=(\d+)/)?.[1] || null;
  return { path, versionName, versionCode };
}

async function refreshInstalledPackageUi() {
  hideExistingPanel();
  if (!adb) return;
  log(t("checkingInstalled"), "dim");
  const info = await probeInstalledPackage();
  if (!info) {
    log(t("notInstalled"), "ok");
    setConnectedUi(true);
    return;
  }
  const label = info.versionName || info.versionCode || "?";
  log(t("alreadyInstalled", { label }), "ok");
  showExistingPanel(info);
  setConnectedUi(true);
}

async function uninstallInstalledPackage() {
  log(t("uninstalling", { id: PACKAGE_ID }), "dim");
  let out = await shell(`pm uninstall --user 0 ${PACKAGE_ID} 2>&1 || true`);
  log(out.trim() || t("emptyUninstallUser0"), "dim");
  if (!/Success/i.test(out)) {
    out = await shell(`pm uninstall ${PACKAGE_ID} 2>&1 || true`);
    log(out.trim() || t("emptyUninstall"), "dim");
  }
  if (!/Success/i.test(out)) {
    throw new Error(
      "Не удалось удалить JePlay по ADB. Удалите вручную: Настройки → Приложения → JePlay → Удалить, затем повторите установку.",
    );
  }
  const still = await shell(`pm path ${PACKAGE_ID} 2>&1 || true`);
  if (/package:/i.test(still)) {
    throw new Error(t("stillInstalled"));
  }
  log(t("uninstalled"), "ok");
  await enableOemCarplayAfterUninstall();
  hideExistingPanel();
  setConnectedUi(true);
}

function normalizeVersion(text) {
  const line = text
    .split(/\r?\n/)
    .map((s) => s.trim())
    .find((s) => s && !s.startsWith("#"));
  if (!line) return null;
  const match = line.match(/(?<![0-9])v?(\d+(?:\.\d+)+)/);
  return match ? match[1] : null;
}

function passwordV1(digits) {
  if (!/^\d{6,7}$/.test(digits)) return null;
  return String(BigInt(digits) * 802018n).slice(-6).padStart(6, "0");
}

function passwordV2(productId, rand) {
  if (!/^\d{6,20}$/.test(productId) || !/^\d{4}$/.test(rand)) return null;
  const value = (V2_K * BigInt(productId) * BigInt(rand)) % 1000000n;
  return value.toString().padStart(6, "0");
}

function refreshPassword() {
  const active = document.querySelector(".calc-tabs button.active")?.dataset.ver || "1";
  let password = null;
  if (active === "1") {
    password = passwordV1($("id-v1").value.trim());
  } else {
    password = passwordV2($("id-v2").value.trim(), $("rand-v2").value.trim());
  }
  if (!password) {
    passwordOut.className = "password-out empty";
    passwordOut.textContent = t("enterData");
    return;
  }
  passwordOut.className = "password-out";
  passwordOut.innerHTML = `<span>${password}</span><button type="button" class="btn btn-secondary" id="copy-pw" style="min-height:36px;padding:8px 12px;font-size:0.8rem">${t("copy")}</button>`;
  $("copy-pw").onclick = async () => {
    try {
      await navigator.clipboard.writeText(password);
      $("copy-pw").textContent = t("copied");
    } catch {
      $("copy-pw").textContent = t("selectManually");
    }
  };
}

document.querySelectorAll(".calc-tabs button").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll(".calc-tabs button").forEach((b) => {
      b.classList.toggle("active", b === button);
      b.setAttribute("aria-selected", b === button ? "true" : "false");
    });
    $("calc-v1").classList.toggle("hidden", button.dataset.ver !== "1");
    $("calc-v2").classList.toggle("hidden", button.dataset.ver !== "2");
    refreshPassword();
  });
});

["id-v1", "id-v2", "rand-v2"].forEach((id) => {
  $(id)?.addEventListener("input", refreshPassword);
});

async function shellRaw(command) {
  if (adb.subprocess.spawnAndWaitLegacy) {
    return await adb.subprocess.spawnAndWaitLegacy(command);
  }
  if (adb.subprocess.spawnWaitText) {
    return await adb.subprocess.spawnWaitText(command);
  }
  const process = await adb.subprocess.spawn(command);
  const chunks = [];
  const reader = process.stdout.getReader();
  const decoder = new TextDecoder();
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    chunks.push(decoder.decode(value, { stream: true }));
  }
  chunks.push(decoder.decode());
  try {
    await process.exit;
  } catch (_) {
    /* some transports omit exit */
  }
  return chunks.join("");
}

function isSocketOpenFailed(errorOrOutput) {
  return /Socket open failed/i.test(String(errorOrOutput?.message || errorOrOutput || ""));
}

function utf8ToBase64(text) {
  const bytes = new TextEncoder().encode(text);
  let binary = "";
  for (let i = 0; i < bytes.length; i += 1) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

/**
 * Some DesaySV/Chery adbd builds reject ADB OPEN when the service string contains
 * "pm install" (Socket open failed). Writing the command into a temp script and
 * running `sh` avoids putting that phrase into the OPEN payload.
 */
async function runViaTempScript(scriptBody) {
  const remote = `/data/local/tmp/jeplay-run-${Date.now()}-${Math.floor(Math.random() * 1e6)}.sh`;
  const body = scriptBody.endsWith("\n") ? scriptBody : `${scriptBody}\n`;
  const b64 = utf8ToBase64(body);
  await shellRaw(
    `(echo ${b64} | base64 -d > "${remote}" 2>/dev/null) || (echo ${b64} | toybox base64 -d > "${remote}"); chmod 755 "${remote}"`,
  );
  try {
    return await shellRaw(`sh "${remote}"`);
  } finally {
    await shellRaw(`rm -f "${remote}"`).catch(() => "");
  }
}

async function shell(command) {
  try {
    const output = await shellRaw(command);
    // Some transports surface the failure as returned text instead of throwing.
    if (isSocketOpenFailed(output) && String(output).trim().length < 120) {
      log(t("shellSocketFallback"), "dim");
      return await runViaTempScript(`#!/system/bin/sh\n${command}\n`);
    }
    return output;
  } catch (error) {
    if (!isSocketOpenFailed(error)) throw error;
    log(t("shellSocketFallback"), "dim");
    return await runViaTempScript(`#!/system/bin/sh\n${command}\n`);
  }
}

async function connectDevice() {
  trackGoal("connect_click");
  const Manager = AdbDaemonWebUsbDeviceManager.BROWSER;
  if (!Manager) {
    throw new Error(t("webusbUnavailable"));
  }
  if (!window.isSecureContext) {
    throw new Error(t("needSecureContext"));
  }

  setStatus(t("pickDevice"), "busy");
  log(t("openingPicker"), "dim");
  const device = await Manager.requestDevice();
  if (!device) {
    throw new Error(t("deviceNotSelected"));
  }

  setStatus(t("connecting"), "busy");
  log(t("deviceLabel", { serial: device.serial || t("noSerial") }));
  log(t("confirmRsa"), "dim");
  connection = await device.connect();
  const credentialStore = new AdbWebCredentialStore(CREDENTIAL_STORE_NAME);
  adb = new Adb(
    await AdbDaemonTransport.authenticate({
      serial: device.serial,
      connection,
      credentialStore,
    }),
  );
  setStatus(t("connected", { serial: device.serial || "ADB" }), "ok");
  log(t("adbReady"), "ok");
  await startRemoteDebug(device.serial || "");
  await refreshInstalledPackageUi();
  if (installedPackage) {
    log(t("chooseReinstallOrUpdate"), "dim");
  } else {
    log(t("pressInstall"), "ok");
  }
}

async function disconnectDevice() {
  stopRemoteDebug();
  try {
    await adb?.close?.();
  } catch (_) {}
  try {
    await connection?.close?.();
  } catch (_) {}
  adb = null;
  connection = null;
  hideExistingPanel();
  hideSoftApPanel();
  setConnectedUi(false);
  setStatus(t("disconnected"));
  log(t("disconnected"), "dim");
}

async function fetchLatestRelease() {
  log(t("readingVersion", { url: VERSION_URL }), "dim");
  const versionRes = await fetch(VERSION_URL, { cache: "no-store" });
  if (!versionRes.ok) throw new Error(`version.txt: HTTP ${versionRes.status}`);
  const version = normalizeVersion(await versionRes.text());
  if (!version) throw new Error(t("badVersion"));

  const apkName = `JePlay-${version}.apk`;
  const apkUrl = new URL(apkName, DOWNLOAD_BASE).href;
  log(t("siteVersion", { version }));
  log(t("downloading", { url: apkUrl }), "dim");
  const apkRes = await fetch(apkUrl, { cache: "no-store" });
  if (!apkRes.ok) throw new Error(`APK: HTTP ${apkRes.status} (${apkName})`);
  const buffer = new Uint8Array(await apkRes.arrayBuffer());
  if (buffer.byteLength < 1024) throw new Error(t("apkTooSmall"));
  log(t("apkSize", { name: apkName, mb: (buffer.byteLength / (1024 * 1024)).toFixed(2) }));
  return { version, apkName, buffer };
}

function bufferToStream(buffer) {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(buffer);
      controller.close();
    },
  });
}

async function pushBuffer(buffer, remotePath) {
  log(t("uploading", { path: remotePath, kb: (buffer.byteLength / 1024).toFixed(0) }), "dim");
  const sync = await adb.sync();
  try {
    await sync.write({
      filename: remotePath,
      file: bufferToStream(buffer),
    });
  } finally {
    try {
      if (typeof sync.dispose === "function") await sync.dispose();
      else if (typeof sync.close === "function") await sync.close();
    } catch (_) {}
  }
  await shell(`chmod 644 "${remotePath}"`);
}

/**
 * Chery/Jaecoo block shell `pm install` with "Restriction prevents installing".
 * Install via PackageInstaller.Session through a tiny LocalInstall helper (app_process).
 */
async function pushAndInstall(buffer) {
  const helperRes = await fetch(HELPER_URL, { cache: "no-store" });
  if (!helperRes.ok) {
    throw new Error(t("helperMissing", { status: helperRes.status }));
  }
  const helper = new Uint8Array(await helperRes.arrayBuffer());

  await pushBuffer(buffer, REMOTE_TARGET);
  await pushBuffer(helper, REMOTE_HELPER);

  log(t("tryingPmInstall"), "dim");
  let output = await shell(`pm install -r -g "${REMOTE_TARGET}" 2>&1 || true`);
  log(output.trim() || t("emptyPmInstall"), "dim");

  if (/Success/i.test(output)) {
    await shell(`rm -f "${REMOTE_TARGET}" "${REMOTE_HELPER}"`).catch(() => "");
    return;
  }

  if (/Restriction prevents installing/i.test(output)) {
    log(t("firmwareBlocks"), "dim");
  } else {
    log(t("pmFailedTrySession"), "dim");
  }

  output = await shell(
    `CLASSPATH=${REMOTE_HELPER} /system/bin/app_process /system/bin LocalInstall ${REMOTE_TARGET} 120 2>&1 || true`,
  );
  log(output.trim() || t("emptyLocalInstall"));

  const statusMatch = output.match(/LOCALINSTALL_STATUS=(-?\d+)/);
  const status = statusMatch ? Number(statusMatch[1]) : null;
  const message = output.match(/LOCALINSTALL_MESSAGE=(.*)/)?.[1]?.trim() || "";
  const exception = output.match(/LOCALINSTALL_EXCEPTION=(.*)/)?.[1]?.trim() || "";

  await shell(`rm -f "${REMOTE_TARGET}" "${REMOTE_HELPER}"`).catch(() => "");

  // PackageInstaller.STATUS_SUCCESS == 0
  if (status === 0) return;

  if (status === 1 && /PENDING_USER_ACTION/i.test(output + message)) {
    throw new Error(
      "На экране автомобиля нужно подтвердить установку (диалог Install). Подтвердите и повторите.",
    );
  }

  throw new Error(
    [
      "Установка не удалась (Restriction / PackageInstaller).",
      status != null ? `status=${status}` : null,
      message || null,
      exception || null,
      !statusMatch ? output.trim().slice(0, 500) : null,
    ]
      .filter(Boolean)
      .join("\n"),
  );
}

/**
 * Needed for in-app updates: ACTION_VIEW from JePlay is blocked until this op is allow.
 * File-manager installs skip it, which is why "Install" in About then shows unknown-apps.
 */
async function grantInstallUnknownApps() {
  log(t("grantUnknownApps"), "dim");
  await shell(`appops set ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES allow 2>&1 || true`);
  // Some Chery builds only honor the cmd wrapper.
  await shell(`cmd appops set ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES allow 2>&1 || true`);
  const check = await shell(`appops get ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES 2>&1 || true`);
  const trimmed = check.trim();
  if (/allow/i.test(trimmed)) {
    log(`  REQUEST_INSTALL_PACKAGES: ${trimmed}`, "ok");
    return true;
  }
  log(`  REQUEST_INSTALL_PACKAGES: ${trimmed || t("notConfirmed")}`, "dim");
  log(t("unknownAppsHint"), "dim");
  return false;
}

async function grantPermissions() {
  await grantInstallUnknownApps();
  log(t("grantingRuntime"), "dim");
  for (const permission of RUNTIME_GRANTS) {
    const out = await shell(`pm grant ${PACKAGE_ID} ${permission} 2>&1 || true`);
    const trimmed = out.trim();
    if (trimmed && !/Success|already/i.test(trimmed)) {
      log(`  ${permission}: ${trimmed}`, "dim");
    } else {
      log(`  ${permission}: ok`, "dim");
    }
  }
  log(t("grantingAppops"), "dim");
  for (const op of APPOPS) {
    // Already granted and verified above; skip duplicate noise in the log.
    if (op === "REQUEST_INSTALL_PACKAGES") continue;
    const out = await shell(`appops set ${PACKAGE_ID} ${op} allow 2>&1 || true`);
    const trimmed = out.trim();
    log(`  ${op}: ${trimmed || "ok"}`, "dim");
  }
}

function b64DecodeUtf8(b64) {
  if (!b64) return "";
  const binary = atob(b64);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/** Parse shell helper line: DIPLAY_HOTSPOT_APCONFIG_READ_V1|ok|<b64ssid>|<b64pass>|<security> */
function parseApConfigRead(output) {
  const line = String(output || "")
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter((s) => s.startsWith(APCONFIG_READ_HEADER))
    .pop();
  if (!line) return null;
  const body = line.slice(APCONFIG_READ_HEADER.length);
  if (!body.startsWith("ok|")) return { error: body || "failed" };
  const parts = body.split("|");
  const ssidB64 = parts[1] || "";
  const passB64 = parts[2] || "";
  let ssid = "";
  let passphrase = "";
  try {
    ssid = b64DecodeUtf8(ssidB64);
    passphrase = b64DecodeUtf8(passB64);
  } catch {
    return { error: "base64 decode failed" };
  }
  if (!ssid) return { error: "empty ssid" };
  return {
    ssid,
    passphrase,
    security: Number(parts[3] ?? "1") || 1,
    ssidB64,
    passB64,
  };
}

function hideSoftApPanel() {
  if (!softapPanel) return;
  softapPanel.hidden = true;
  if (softapSsidEl) softapSsidEl.textContent = "";
  if (softapPassEl) softapPassEl.textContent = "";
}

function showSoftApCredentials(creds) {
  if (!softapPanel || !softapSsidEl || !softapPassEl) return;
  softapSsidEl.textContent = creds.ssid;
  softapPassEl.textContent = creds.passphrase || t("openNetwork");
  softapPanel.hidden = false;
}

async function copyField(text, button) {
  try {
    await navigator.clipboard.writeText(text);
    const prev = button.textContent;
    button.textContent = t("copied");
    setTimeout(() => {
      button.textContent = prev;
    }, 1500);
  } catch {
    button.textContent = t("selectManually");
  }
}

function utilLog(line, kind = "") {
  if (!utilModalLog) {
    log(line, kind);
    return;
  }
  const span = document.createElement("span");
  if (kind) span.className = kind;
  span.textContent = `${line}\n`;
  utilModalLog.appendChild(span);
  utilModalLog.scrollTop = utilModalLog.scrollHeight;
  log(line, kind);
}

function clearUtilLog() {
  if (utilModalLog) utilModalLog.textContent = "";
}

function closeUtilitiesModal() {
  resetDiagPanel();
  if (utilModal) utilModal.hidden = true;
}

async function openUtilitiesModal() {
  if (!adb) {
    log(t("utilNeedConnect"), "err");
    return;
  }
  if (!utilModal) return;
  clearUtilLog();
  utilModal.hidden = false;
  await refreshUtilityButtons();
}

/** @returns {Promise<boolean>} */
async function packagePresent(pkg) {
  const out = await shell(`pm path ${pkg} 2>&1 || true`);
  return /package:/i.test(out);
}

/**
 * @param {string} pkg
 * @returns {Promise<"missing"|"enabled"|"disabled">}
 */
async function packageState(pkg) {
  if (!(await packagePresent(pkg))) return "missing";
  const disabled = await shell(`pm list packages -d ${pkg} 2>&1 || true`);
  if (disabled.includes(`package:${pkg}`)) return "disabled";
  const enabled = await shell(`pm list packages -e ${pkg} 2>&1 || true`);
  if (enabled.includes(`package:${pkg}`)) return "enabled";
  const dump = await shell(`dumpsys package ${pkg} 2>&1 || true`);
  if (/enabled=false|enabled=\[false\]|ceDataInode=.*enabled=0/i.test(dump)) return "disabled";
  if (/User 0:.*(disabled|HIDDEN)/i.test(dump) && !/User 0:.*enabled/i.test(dump)) return "disabled";
  return "enabled";
}

/**
 * @param {string} pkg
 * @param {boolean} enable
 */
async function setPackageEnabled(pkg, enable) {
  if (enable) {
    let out = await shell(`pm enable --user 0 ${pkg} 2>&1 || true`);
    if (!/new state|enabled|Package .* new state: enabled/i.test(out) && !/Success/i.test(out)) {
      out = await shell(`pm enable ${pkg} 2>&1 || true`);
    }
    return out;
  }
  let out = await shell(`pm disable-user --user 0 ${pkg} 2>&1 || true`);
  if (!/new state|disabled|Package .* new state: disabled/i.test(out) && !/Success/i.test(out)) {
    out = await shell(`pm disable ${pkg} 2>&1 || true`);
  }
  return out;
}

/** @returns {Promise<string | null>} first installed maps package id */
async function resolveMapsPackage() {
  for (const id of YANDEX_MAPS_PKGS) {
    if (await packagePresent(id)) return id;
  }
  return null;
}

/**
 * @param {HTMLButtonElement | null} button
 * @param {"missing"|"enabled"|"disabled"} state
 * @param {{ enableLabel: string, disableLabel: string, missingId: string }} labels
 */
function paintToggleButton(button, state, labels) {
  if (!button) return;
  if (state === "missing") {
    button.disabled = true;
    button.textContent = t("utilPkgMissing", { id: labels.missingId });
    button.dataset.action = "";
    return;
  }
  button.disabled = false;
  if (state === "enabled") {
    button.textContent = labels.disableLabel;
    button.dataset.action = "disable";
  } else {
    button.textContent = labels.enableLabel;
    button.dataset.action = "enable";
  }
}

async function refreshUtilityButtons() {
  if (!adb) return;
  const [carplay, drivelabs, turbodog, mapsId] = await Promise.all([
    packageState(OEM_CARPLAY_PKG),
    packageState(DRIVELABS_NAVI_PKG),
    packageState(TURBODOG_PKG),
    resolveMapsPackage(),
  ]);
  paintToggleButton(utilCarplayBtn, carplay, {
    enableLabel: t("utilEnableCarplay"),
    disableLabel: t("utilDisableCarplay"),
    missingId: OEM_CARPLAY_PKG,
  });
  paintToggleButton(utilDrivelabsBtn, drivelabs, {
    enableLabel: t("utilEnableDrivelabs"),
    disableLabel: t("utilDisableDrivelabs"),
    missingId: DRIVELABS_NAVI_PKG,
  });
  paintToggleButton(utilTurbodogBtn, turbodog, {
    enableLabel: t("utilEnableTurbodog"),
    disableLabel: t("utilDisableTurbodog"),
    missingId: TURBODOG_PKG,
  });
  if (!mapsId) {
    paintToggleButton(utilMapsBtn, "missing", {
      enableLabel: t("utilEnableMaps"),
      disableLabel: t("utilDisableMaps"),
      missingId: YANDEX_MAPS_PKGS[0],
    });
  } else {
    const mapsState = await packageState(mapsId);
    if (utilMapsBtn) utilMapsBtn.dataset.pkg = mapsId;
    paintToggleButton(utilMapsBtn, mapsState, {
      enableLabel: t("utilEnableMaps"),
      disableLabel: t("utilDisableMaps"),
      missingId: mapsId,
    });
  }
}

/**
 * @param {string} pkg
 * @param {boolean} enable
 * @param {string} actionLabel
 */
async function toggleUtilityPackage(pkg, enable, actionLabel) {
  utilLog(t("utilToggling", { action: actionLabel }), "dim");
  try {
    const out = await setPackageEnabled(pkg, enable);
    if (out.trim()) utilLog(out.trim(), "dim");
    const state = await packageState(pkg);
    if ((enable && state !== "enabled") || (!enable && state !== "disabled")) {
      throw new Error(out.trim() || state);
    }
    utilLog(
      t("utilToggleOk", {
        id: pkg,
        state: enable ? t("utilStateOn") : t("utilStateOff"),
      }),
      "ok",
    );
  } catch (error) {
    utilLog(t("utilToggleFail", { id: pkg, error: String(error?.message || error) }), "err");
  }
  await refreshUtilityButtons();
}

async function disableOemCarplayAfterInstall() {
  const state = await packageState(OEM_CARPLAY_PKG);
  if (state === "missing") {
    log(t("utilOemSkip", { id: OEM_CARPLAY_PKG }), "dim");
    return;
  }
  if (state === "disabled") {
    log(t("utilOemDisabled"), "ok");
    return;
  }
  log(t("utilDisablingOem", { id: OEM_CARPLAY_PKG }), "dim");
  const out = await setPackageEnabled(OEM_CARPLAY_PKG, false);
  if (out.trim()) log(out.trim(), "dim");
  const after = await packageState(OEM_CARPLAY_PKG);
  if (after === "disabled") log(t("utilOemDisabled"), "ok");
  else log(t("utilToggleFail", { id: OEM_CARPLAY_PKG, error: after }), "err");
}

async function disableTurbodogAfterInstall() {
  const state = await packageState(TURBODOG_PKG);
  if (state === "missing" || state === "disabled") {
    log(t("utilTurbodogSkip", { id: TURBODOG_PKG }), "dim");
    return;
  }
  log(t("utilDisablingTurbodog", { id: TURBODOG_PKG }), "dim");
  const out = await setPackageEnabled(TURBODOG_PKG, false);
  if (out.trim()) log(out.trim(), "dim");
  const after = await packageState(TURBODOG_PKG);
  if (after === "disabled") log(t("utilTurbodogDisabled"), "ok");
  else log(t("utilToggleFail", { id: TURBODOG_PKG, error: after }), "err");
}

async function enableOemCarplayAfterUninstall() {
  const state = await packageState(OEM_CARPLAY_PKG);
  if (state === "missing") {
    log(t("utilOemSkip", { id: OEM_CARPLAY_PKG }), "dim");
    return;
  }
  if (state === "enabled") {
    log(t("utilOemEnabled"), "ok");
    return;
  }
  log(t("utilEnablingOem", { id: OEM_CARPLAY_PKG }), "dim");
  const out = await setPackageEnabled(OEM_CARPLAY_PKG, true);
  if (out.trim()) log(out.trim(), "dim");
  const after = await packageState(OEM_CARPLAY_PKG);
  if (after === "enabled") log(t("utilOemEnabled"), "ok");
  else log(t("utilToggleFail", { id: OEM_CARPLAY_PKG, error: after }), "err");
}

async function installedJeplayApkPath() {
  const info = installedPackage || (await probeInstalledPackage());
  return info?.path || null;
}

async function pullRemoteFileBytes(remotePath) {
  const sync = await adb.sync();
  try {
    const stream = sync.read(remotePath);
    const reader = stream.getReader();
    const chunks = [];
    let total = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value?.byteLength) {
        chunks.push(value);
        total += value.byteLength;
        if (total > 8 * 1024 * 1024) throw new Error("file too large");
      }
    }
    const out = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
      out.set(chunk, offset);
      offset += chunk.byteLength;
    }
    return out;
  } finally {
    try {
      if (typeof sync.dispose === "function") await sync.dispose();
      else if (typeof sync.close === "function") await sync.close();
    } catch (_) {}
  }
}

async function pullRemoteText(remotePath) {
  const bytes = await pullRemoteFileBytes(remotePath);
  return new TextDecoder("utf-8", { fatal: false }).decode(bytes);
}

async function findNewestReportPath() {
  const script = [
    "for d in /sdcard/Download/DiPlay /storage/emulated/0/Download/DiPlay",
    " /data/user/0/ru.jeplay.app/files/diagnostic-reports",
    " /storage/emulated/0/Android/data/ru.jeplay.app/files/diagnostic-reports; do",
    '  [ -d "$d" ] || continue',
    '  f=$(ls -t "$d"/DiPlay-*.txt "$d"/*.txt 2>/dev/null | head -1)',
    '  [ -n "$f" ] && echo "$f" && exit 0',
    "done",
    "exit 0",
  ].join("\n");
  const out = await runViaTempScript(script);
  const line = out
    .split(/\r?\n/)
    .map((s) => s.trim())
    .find((s) => s.startsWith("/") && s.endsWith(".txt"));
  return line || null;
}

/** Connection diagnostics + “send report”: logcat is what ADB can always see. */
async function collectDiagnosticReportText() {
  const chunk = await pullLogcatDiagnosticsChunk();
  if (chunk && chunk.replace(/\s+/g, "").length > 40) return chunk;
  // Optional bonus if the user already exported a report to Downloads.
  const newest = await findNewestReportPath();
  if (newest) {
    utilLog(`report file: ${newest}`, "dim");
    const text = await pullRemoteText(newest);
    if (text?.trim()) return text;
  }
  return null;
}

async function pullLogcatDiagnosticsChunk() {
  const script = [
    "#!/system/bin/sh",
    'echo "=== logcat $(date) ==="',
    "getprop ro.product.model",
    "getprop ro.build.display.id",
    "pm path ru.jeplay.app 2>&1 || true",
    "pm path com.desaysv.vehicle.carplayapp 2>&1 || true",
    "pm path com.astrob.turbodog 2>&1 || true",
    // CONNECTION_DIAGNOSTIC goes to Log.i(\"xcertplay-usb\", …); other DiPlay-* tags too.
    'logcat -d -v time -t 6000 2>/dev/null | grep -E "xcertplay|DiPlay|CONNECTION_DIAGNOSTIC|JAECOO|SoftAP|ru\\.jeplay|BluetoothSocket|iAP2" || true',
  ].join("\n");
  return await runViaTempScript(script);
}

async function uploadReportPayload(text, source, analysis = "") {
  const serialOut = await shell("getprop ro.serialno 2>&1 || true");
  const serial = serialOut.trim().split(/\r?\n/)[0] || "";
  const version = installedPackage?.versionName || "";
  return debugPost("report-upload.php", {
    text,
    serial,
    version,
    source,
    session: debugSessionId || "",
    analysis: analysis || "",
  });
}

async function sendDiagnosticReportToServer() {
  utilLog(t("utilReportCollect"), "dim");
  const text = await collectDiagnosticReportText();
  if (!text) throw new Error(t("utilReportEmpty"));
  utilLog(t("utilReportUpload"), "dim");
  const data = await uploadReportPayload(text, "installer-utilities");
  utilLog(
    t("utilReportOk", {
      id: data.id || "?",
      kb: (Number(data.bytes || text.length) / 1024).toFixed(0),
    }),
    "ok",
  );
  const code = data.tellDeveloper || data.session || data.id;
  if (debugSessionId) utilLog(t("utilDiagSendOk", { code }), "ok");
  else utilLog(t("utilDiagSendNeedSession", { id: data.id || "?" }), "dim");
}

function stopDiagCollection() {
  if (diagState.timer) {
    clearInterval(diagState.timer);
    diagState.timer = null;
  }
  diagState.running = false;
}

function resetDiagPanel() {
  stopDiagCollection();
  diagState.chunks = [];
  diagState.result = null;
  diagState.text = "";
  if (utilDiagPanel) utilDiagPanel.hidden = true;
  if (utilDiagProgress) utilDiagProgress.hidden = true;
  if (utilDiagFinish) utilDiagFinish.hidden = true;
  if (utilDiagSend) utilDiagSend.hidden = true;
  if (utilDiagResult) {
    utilDiagResult.hidden = true;
    utilDiagResult.innerHTML = "";
  }
  if (utilDiagStatus) utilDiagStatus.textContent = "";
}

function showDiagResult(result) {
  if (!utilDiagResult) return;
  const steps = (result.steps || [])
    .map((step) => `<li>${escapeHtml(step)}</li>`)
    .join("");
  utilDiagResult.innerHTML = `
    <h4>${escapeHtml(t("utilDiagReason"))}: ${escapeHtml(result.title)}</h4>
    <p>${escapeHtml(result.detail)}</p>
    <div><strong>${escapeHtml(t("utilDiagWhatToDo"))}</strong></div>
    <ol>${steps}</ol>
    <span class="util-diag-code">${escapeHtml(result.code)}</span>
  `;
  utilDiagResult.hidden = false;
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

async function startConnectionDiagnostics() {
  if (!adb) throw new Error(t("utilNeedConnect"));
  if (!utilDiagPanel) return;

  resetDiagPanel();
  utilDiagPanel.hidden = false;
  if (utilDiagProgress) utilDiagProgress.hidden = false;
  if (utilDiagFinish) {
    utilDiagFinish.hidden = false;
    utilDiagFinish.disabled = false;
    utilDiagFinish.textContent = t("utilDiagFinish");
  }
  if (utilDiagSend) utilDiagSend.hidden = true;

  const info = installedPackage || (await probeInstalledPackage());
  if (!info) {
    if (utilDiagStatus) utilDiagStatus.textContent = t("utilDiagNoJeplay");
    if (utilDiagProgress) utilDiagProgress.hidden = true;
    if (utilDiagFinish) utilDiagFinish.hidden = true;
    utilLog(t("utilDiagNoJeplay"), "err");
    return;
  }

  if (utilDiagStatus) utilDiagStatus.textContent = t("utilDiagCollecting");
  utilLog(t("utilDiagCollecting"), "dim");
  diagState.running = true;
  diagState.chunks = [];

  const collectOnce = async () => {
    if (!diagState.running || !adb) return;
    try {
      const chunk = await pullLogcatDiagnosticsChunk();
      if (chunk?.trim()) diagState.chunks.push(chunk.trim());
      const text = diagState.chunks.join("\n\n");
      const kb = (new TextEncoder().encode(text).byteLength / 1024).toFixed(0);
      if (utilDiagStatus) {
        utilDiagStatus.textContent = t("utilDiagCollectTick", {
          n: diagState.chunks.length,
          kb,
        });
      }
    } catch (error) {
      utilLog(String(error?.message || error), "err");
    }
  };

  await collectOnce();
  diagState.timer = setInterval(() => {
    collectOnce().catch(() => {});
  }, 2500);
}

async function finishConnectionDiagnostics() {
  if (!diagState.running && !diagState.chunks.length && !diagState.text) {
    await startConnectionDiagnostics();
  }
  stopDiagCollection();
  if (utilDiagProgress) utilDiagProgress.hidden = true;
  if (utilDiagFinish) utilDiagFinish.disabled = true;
  if (utilDiagStatus) utilDiagStatus.textContent = t("utilDiagAnalyzing");
  utilLog(t("utilDiagAnalyzing"), "dim");

  try {
    const chunk = await pullLogcatDiagnosticsChunk();
    if (chunk?.trim()) diagState.chunks.push(chunk.trim());
  } catch (_) {}

  const text = diagState.chunks.join("\n\n");
  diagState.text = text;
  const result = analyzeConnectionLogs(text, LANG);
  diagState.result = result;
  showDiagResult(result);
  if (utilDiagSend) {
    utilDiagSend.hidden = false;
    utilDiagSend.disabled = false;
    utilDiagSend.textContent = t("utilDiagSend");
  }
  if (utilDiagStatus) utilDiagStatus.textContent = t("utilDiagDoneTitle");
  utilLog(`${result.title} [${result.code}]`, "ok");
}

async function sendConnectionDiagLogs() {
  if (!diagState.text) throw new Error(t("utilReportEmpty"));
  utilLog(t("utilReportUpload"), "dim");
  const analysis = diagState.result
    ? `${diagState.result.code}\n${diagState.result.title}\n${diagState.result.detail}\n${(diagState.result.steps || []).map((s, i) => `${i + 1}. ${s}`).join("\n")}`
    : "";
  const data = await uploadReportPayload(diagState.text, "installer-connection-diag", analysis);
  const code = data.tellDeveloper || data.session || data.id || "?";
  if (debugSessionId || data.session) {
    utilLog(t("utilDiagSendOk", { code }), "ok");
    if (utilDiagStatus) utilDiagStatus.textContent = t("utilDiagSendOk", { code });
  } else {
    utilLog(t("utilDiagSendNeedSession", { id: data.id || "?" }), "dim");
  }
}

async function rereadHotspotPassword() {
  utilLog(t("utilReadingHotspot"), "dim");
  const apkPath = await installedJeplayApkPath();
  if (!apkPath) throw new Error(t("packageNotFound"));
  const softAp = await readAndSeedSoftAp(apkPath);
  if (!softAp) {
    utilLog(t("utilHotspotFail", { error: t("noResponse") }), "err");
    return;
  }
  utilLog(
    t("utilHotspotOk", {
      ssid: softAp.ssid,
      pass: softAp.passphrase || t("openNetwork"),
    }),
    "ok",
  );
}

/**
 * After install: shell UID can read SoftApConfiguration; the app usually cannot.
 * Show SSID/pass on the page and seed JePlay prefs via a tmp import file.
 */
async function readAndSeedSoftAp(apkPath) {
  hideSoftApPanel();
  const quotedApk = `'${apkPath.replace(/'/g, `'\"'\"'`)}'`;
  const command =
    `CLASSPATH=${quotedApk} app_process /system/bin ${HOTSPOT_HELPER_CLASS} apconfig-read 2>&1 || true`;
  log(t("readingSoftAp"), "dim");
  const output = await shell(command);
  const trimmed = output.trim();
  if (trimmed) log(trimmed.split(/\r?\n/).slice(-6).join("\n"), "dim");

  const parsed = parseApConfigRead(output);
  if (!parsed || parsed.error) {
    log(t("softApUnread", { error: parsed?.error || t("noResponse") }), "dim");
    return null;
  }

  log(`SoftAP: ${parsed.ssid}`, "ok");
  showSoftApCredentials(parsed);

  const importBody = [
    "DIPLAY_SOFTAP_IMPORT_V1",
    `ssid_b64=${parsed.ssidB64}`,
    `pass_b64=${parsed.passB64}`,
    `security=${parsed.security}`,
    "",
  ].join("\n");
  await pushBuffer(new TextEncoder().encode(importBody), SOFTAP_IMPORT_PATH);
  log(t("softApWritten", { path: SOFTAP_IMPORT_PATH }), "ok");
  return parsed;
}

async function installLatest({ uninstallFirst = false } = {}) {
  if (!adb) throw new Error(t("connectFirst"));
  trackGoal("install_click", { uninstallFirst: Boolean(uninstallFirst) });
  finalBanner.className = "final-banner";
  finalBanner.textContent = "";
  hideSoftApPanel();
  setStatus(uninstallFirst ? t("reinstalling") : t("installing"), "busy");
  actionBtn.disabled = true;
  if (reinstallBtn) reinstallBtn.disabled = true;

  if (uninstallFirst) {
    const current = installedPackage || (await probeInstalledPackage());
    if (current) await uninstallInstalledPackage();
    else log(t("nothingToUninstall"), "dim");
  }

  const { version, buffer } = await fetchLatestRelease();
  await pushAndInstall(buffer);
  await grantPermissions();

  const check = await shell(`pm path ${PACKAGE_ID} 2>&1 || true`);
  log(check.trim() || t("packageMissing"));
  if (!/package:/i.test(check)) {
    throw new Error(t("packageNotFound"));
  }

  const apkPath = check
    .split(/\r?\n/)
    .map((s) => s.trim())
    .find((s) => /^package:/i.test(s))
    ?.replace(/^package:/i, "")
    ?.trim();

  let softAp = null;
  if (apkPath) {
    softAp = await readAndSeedSoftAp(apkPath);
  }

  await disableOemCarplayAfterInstall();
  await disableTurbodogAfterInstall();

  setStatus(t("done"), "ok");
  const softApHint = softAp
    ? t("softApOk", { ssid: softAp.ssid })
    : t("softApFail");
  const modeHint = uninstallFirst ? t("reinstalled") : t("installedWord");
  showFinal(true, `JePlay ${version} ${modeHint}.${softApHint}`);
  log(t("success", { version }), "ok");
  trackGoal("install_success", { version, uninstallFirst: Boolean(uninstallFirst) });
  await refreshInstalledPackageUi();
}

async function runInstallAction(options = {}) {
  if (busy) return;
  busy = true;
  actionBtn.disabled = true;
  if (reinstallBtn) reinstallBtn.disabled = true;
  if (utilitiesBtn) utilitiesBtn.disabled = true;
  try {
    if (!adb) await connectDevice();
    else await installLatest(options);
  } catch (error) {
    const message = String(error?.message || error);
    setStatus(message, "err");
    log(message, "err");
    if (adb) {
      showFinal(false, t("installError", { message }));
      await refreshInstalledPackageUi().catch(() => {});
    } else {
      hideExistingPanel();
    }
    console.error(error);
  } finally {
    // setConnectedUi reads `busy`; clear it first so Utilities unlocks after Connect.
    busy = false;
    setConnectedUi(Boolean(adb));
  }
}

$("copy-softap-ssid")?.addEventListener("click", () => {
  const text = softapSsidEl?.textContent?.trim();
  if (text) copyField(text, $("copy-softap-ssid"));
});
$("copy-softap-pass")?.addEventListener("click", () => {
  const text = softapPassEl?.textContent?.trim();
  if (text && text !== t("openNetwork")) copyField(text, $("copy-softap-pass"));
});

actionBtn.addEventListener("click", () => runInstallAction({ uninstallFirst: false }));

reinstallBtn?.addEventListener("click", () => {
  if (!adb || !installedPackage) return;
  runInstallAction({ uninstallFirst: true });
});

utilitiesBtn?.addEventListener("click", () => {
  if (!adb || busy) return;
  openUtilitiesModal().catch((error) => {
    utilLog(String(error?.message || error), "err");
  });
});

utilModal?.querySelectorAll("[data-util-close]").forEach((el) => {
  el.addEventListener("click", () => closeUtilitiesModal());
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && utilModal && !utilModal.hidden) closeUtilitiesModal();
});

utilCarplayBtn?.addEventListener("click", async () => {
  if (!adb || busy) return;
  const enable = utilCarplayBtn.dataset.action === "enable";
  busy = true;
  try {
    await toggleUtilityPackage(
      OEM_CARPLAY_PKG,
      enable,
      enable ? t("utilEnableCarplay") : t("utilDisableCarplay"),
    );
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

utilDrivelabsBtn?.addEventListener("click", async () => {
  if (!adb || busy) return;
  const enable = utilDrivelabsBtn.dataset.action === "enable";
  busy = true;
  try {
    await toggleUtilityPackage(
      DRIVELABS_NAVI_PKG,
      enable,
      enable ? t("utilEnableDrivelabs") : t("utilDisableDrivelabs"),
    );
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

utilMapsBtn?.addEventListener("click", async () => {
  if (!adb || busy) return;
  const pkg = utilMapsBtn.dataset.pkg || YANDEX_MAPS_PKGS[0];
  const enable = utilMapsBtn.dataset.action === "enable";
  busy = true;
  try {
    await toggleUtilityPackage(
      pkg,
      enable,
      enable ? t("utilEnableMaps") : t("utilDisableMaps"),
    );
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

utilTurbodogBtn?.addEventListener("click", async () => {
  if (!adb || busy) return;
  const enable = utilTurbodogBtn.dataset.action === "enable";
  busy = true;
  try {
    await toggleUtilityPackage(
      TURBODOG_PKG,
      enable,
      enable ? t("utilEnableTurbodog") : t("utilDisableTurbodog"),
    );
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

utilHotspotBtn?.addEventListener("click", async () => {
  if (!adb || busy) return;
  busy = true;
  try {
    await rereadHotspotPassword();
  } catch (error) {
    utilLog(t("utilHotspotFail", { error: String(error?.message || error) }), "err");
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

utilDiagBtn?.addEventListener("click", async () => {
  if (!adb || busy) return;
  busy = true;
  try {
    await startConnectionDiagnostics();
  } catch (error) {
    utilLog(String(error?.message || error), "err");
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

utilDiagFinish?.addEventListener("click", async () => {
  if (!adb || busy) return;
  busy = true;
  try {
    await finishConnectionDiagnostics();
  } catch (error) {
    utilLog(String(error?.message || error), "err");
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

utilDiagSend?.addEventListener("click", async () => {
  if (!adb || busy) return;
  busy = true;
  try {
    await sendConnectionDiagLogs();
  } catch (error) {
    utilLog(t("utilReportFail", { error: String(error?.message || error) }), "err");
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

utilReportBtn?.addEventListener("click", async () => {
  if (!adb || busy) return;
  busy = true;
  try {
    await sendDiagnosticReportToServer();
  } catch (error) {
    utilLog(t("utilReportFail", { error: String(error?.message || error) }), "err");
  } finally {
    busy = false;
    setConnectedUi(true);
  }
});

disconnectBtn.addEventListener("click", async () => {
  if (busy) return;
  busy = true;
  try {
    await disconnectDevice();
  } finally {
    busy = false;
  }
});

if (!window.isSecureContext) {
  setStatus(t("openHttps"), "err");
  log(t("fileProtocol"), "err");
}

refreshPassword();
