/**
 * JePlay browser installer: WebUSB ADB + local ENCRYPTION password calc.
 * Opens Chrome device picker on Connect; after auth the same button becomes Install.
 */
// Bundled into install.bundle.js — no CDN at runtime (esm.sh often blocked on Windows).
import { Adb, AdbDaemonTransport } from "@yume-chan/adb";
import { AdbDaemonWebUsbDeviceManager } from "@yume-chan/adb-daemon-webusb";
import AdbWebCredentialStore from "@yume-chan/adb-credential-web";

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

const actionBtn = $("action-btn");
const disconnectBtn = $("disconnect-btn");
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

let adb = null;
let connection = null;
let busy = false;
/** @type {{ path: string, versionName: string | null, versionCode: string | null } | null} */
let installedPackage = null;

function setStatus(text, kind = "") {
  statusPill.textContent = text;
  statusPill.className = `status-pill${kind ? ` ${kind}` : ""}`;
}

function log(line, kind = "") {
  const span = document.createElement("span");
  if (kind) span.className = kind;
  span.textContent = `${line}\n`;
  if (logEl.textContent === "Ожидание подключения…") logEl.textContent = "";
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
  } else if (installedPackage) {
    actionBtn.textContent = "Обновить поверх";
  } else {
    actionBtn.textContent = "Установить";
  }
  actionBtn.disabled = false;
  disconnectBtn.disabled = !on;
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
  existingVersionEl.textContent = info.versionName || info.versionCode || "установлен";
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
  log("Проверяю, установлен ли JePlay…", "dim");
  const info = await probeInstalledPackage();
  if (!info) {
    log("JePlay на ГУ не найден — можно ставить с нуля.", "ok");
    setConnectedUi(true);
    return;
  }
  const label = info.versionName || info.versionCode || "?";
  log(`JePlay уже установлен: ${label}`, "ok");
  showExistingPanel(info);
  setConnectedUi(true);
}

async function uninstallInstalledPackage() {
  log(`Удаляю ${PACKAGE_ID}…`, "dim");
  let out = await shell(`pm uninstall --user 0 ${PACKAGE_ID} 2>&1 || true`);
  log(out.trim() || "(пустой ответ pm uninstall --user 0)", "dim");
  if (!/Success/i.test(out)) {
    out = await shell(`pm uninstall ${PACKAGE_ID} 2>&1 || true`);
    log(out.trim() || "(пустой ответ pm uninstall)", "dim");
  }
  if (!/Success/i.test(out)) {
    throw new Error(
      "Не удалось удалить JePlay по ADB. Удалите вручную: Настройки → Приложения → JePlay → Удалить, затем повторите установку.",
    );
  }
  const still = await shell(`pm path ${PACKAGE_ID} 2>&1 || true`);
  if (/package:/i.test(still)) {
    throw new Error("После uninstall пакет всё ещё на месте");
  }
  log("JePlay удалён", "ok");
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
    passwordOut.textContent = "Введите данные выше";
    return;
  }
  passwordOut.className = "password-out";
  passwordOut.innerHTML = `<span>${password}</span><button type="button" class="btn btn-secondary" id="copy-pw" style="min-height:36px;padding:8px 12px;font-size:0.8rem">Копировать</button>`;
  $("copy-pw").onclick = async () => {
    try {
      await navigator.clipboard.writeText(password);
      $("copy-pw").textContent = "Скопировано";
    } catch {
      $("copy-pw").textContent = "Выделите вручную";
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

async function shell(command) {
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

async function connectDevice() {
  trackGoal("connect_click");
  const Manager = AdbDaemonWebUsbDeviceManager.BROWSER;
  if (!Manager) {
    throw new Error("WebUSB недоступен. Откройте эту страницу в Chrome по https:// или http://localhost");
  }
  if (!window.isSecureContext) {
    throw new Error("Нужен secure context: https:// или http://localhost (не file://)");
  }

  setStatus("Выберите устройство…", "busy");
  log("Открываю окно выбора USB-устройства Chrome…", "dim");
  const device = await Manager.requestDevice();
  if (!device) {
    throw new Error("Устройство не выбрано");
  }

  setStatus("Подключение…", "busy");
  log(`Устройство: ${device.serial || "(без serial)"}`);
  log("Если ГУ спросит RSA — подтвердите доступ", "dim");
  connection = await device.connect();
  const credentialStore = new AdbWebCredentialStore(CREDENTIAL_STORE_NAME);
  adb = new Adb(
    await AdbDaemonTransport.authenticate({
      serial: device.serial,
      connection,
      credentialStore,
    }),
  );
  setStatus(`Подключено: ${device.serial || "ADB"}`, "ok");
  log("ADB готов.", "ok");
  await refreshInstalledPackageUi();
  if (installedPackage) {
    log("Выберите: удалить и поставить заново, или обновить поверх.", "dim");
  } else {
    log("Нажмите «Установить».", "ok");
  }
}

async function disconnectDevice() {
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
  setStatus("Отключено");
  log("Отключено", "dim");
}

async function fetchLatestRelease() {
  log(`Читаю ${VERSION_URL}…`, "dim");
  const versionRes = await fetch(VERSION_URL, { cache: "no-store" });
  if (!versionRes.ok) throw new Error(`version.txt: HTTP ${versionRes.status}`);
  const version = normalizeVersion(await versionRes.text());
  if (!version) throw new Error("В version.txt нет распознаваемой версии");

  const apkName = `JePlay-${version}.apk`;
  const apkUrl = new URL(apkName, DOWNLOAD_BASE).href;
  log(`Версия на сайте: ${version}`);
  log(`Скачиваю ${apkUrl}…`, "dim");
  const apkRes = await fetch(apkUrl, { cache: "no-store" });
  if (!apkRes.ok) throw new Error(`APK: HTTP ${apkRes.status} (${apkName})`);
  const buffer = new Uint8Array(await apkRes.arrayBuffer());
  if (buffer.byteLength < 1024) throw new Error("Скачанный APK слишком маленький — проверьте файл на сервере");
  log(`APK ${apkName}: ${(buffer.byteLength / (1024 * 1024)).toFixed(2)} МБ`);
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
  log(`Загрузка → ${remotePath} (${(buffer.byteLength / 1024).toFixed(0)} КБ)`, "dim");
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
    throw new Error(`Не найден helper localinstall.apk на сайте (HTTP ${helperRes.status})`);
  }
  const helper = new Uint8Array(await helperRes.arrayBuffer());

  await pushBuffer(buffer, REMOTE_TARGET);
  await pushBuffer(helper, REMOTE_HELPER);

  log("Пробую pm install…", "dim");
  let output = await shell(`pm install -r -g "${REMOTE_TARGET}" 2>&1 || true`);
  log(output.trim() || "(пустой ответ pm install)", "dim");

  if (/Success/i.test(output)) {
    await shell(`rm -f "${REMOTE_TARGET}" "${REMOTE_HELPER}"`).catch(() => "");
    return;
  }

  if (/Restriction prevents installing/i.test(output)) {
    log("Прошивка блокирует pm install — ставлю через PackageInstaller.Session…", "dim");
  } else {
    log("pm install не сработал — пробую PackageInstaller.Session…", "dim");
  }

  output = await shell(
    `CLASSPATH=${REMOTE_HELPER} /system/bin/app_process /system/bin LocalInstall ${REMOTE_TARGET} 120 2>&1 || true`,
  );
  log(output.trim() || "(пустой ответ LocalInstall)");

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
  log("Разрешаю установку обновлений из JePlay (unknown apps)…", "dim");
  await shell(`appops set ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES allow 2>&1 || true`);
  // Some Chery builds only honor the cmd wrapper.
  await shell(`cmd appops set ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES allow 2>&1 || true`);
  const check = await shell(`appops get ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES 2>&1 || true`);
  const trimmed = check.trim();
  if (/allow/i.test(trimmed)) {
    log(`  REQUEST_INSTALL_PACKAGES: ${trimmed}`, "ok");
    return true;
  }
  log(`  REQUEST_INSTALL_PACKAGES: ${trimmed || "не подтверждено"}`, "dim");
  log("  Обновление кнопкой в JePlay может снова спросить Settings — откройте APK из Downloads.", "dim");
  return false;
}

async function grantPermissions() {
  await grantInstallUnknownApps();
  log("Выдаю runtime-разрешения…", "dim");
  for (const permission of RUNTIME_GRANTS) {
    const out = await shell(`pm grant ${PACKAGE_ID} ${permission} 2>&1 || true`);
    const trimmed = out.trim();
    if (trimmed && !/Success|already/i.test(trimmed)) {
      log(`  ${permission}: ${trimmed}`, "dim");
    } else {
      log(`  ${permission}: ok`, "dim");
    }
  }
  log("Выдаю appops…", "dim");
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
  softapPassEl.textContent = creds.passphrase || "(открытая сеть)";
  softapPanel.hidden = false;
}

async function copyField(text, button) {
  try {
    await navigator.clipboard.writeText(text);
    const prev = button.textContent;
    button.textContent = "Скопировано";
    setTimeout(() => {
      button.textContent = prev;
    }, 1500);
  } catch {
    button.textContent = "Выделите вручную";
  }
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
  log("Читаю SoftAP (SSID/пароль) через ADB…", "dim");
  const output = await shell(command);
  const trimmed = output.trim();
  if (trimmed) log(trimmed.split(/\r?\n/).slice(-6).join("\n"), "dim");

  const parsed = parseApConfigRead(output);
  if (!parsed || parsed.error) {
    log(
      `SoftAP не прочитан (${parsed?.error || "нет ответа"}). Пароль можно ввести в JePlay с iPhone.`,
      "dim",
    );
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
  log(`Записал настройки SoftAP → ${SOFTAP_IMPORT_PATH} (JePlay подхватит при запуске)`, "ok");
  return parsed;
}

async function installLatest({ uninstallFirst = false } = {}) {
  if (!adb) throw new Error("Сначала подключите устройство");
  trackGoal("install_click", { uninstallFirst: Boolean(uninstallFirst) });
  finalBanner.className = "final-banner";
  finalBanner.textContent = "";
  hideSoftApPanel();
  setStatus(uninstallFirst ? "Переустановка…" : "Установка…", "busy");
  actionBtn.disabled = true;
  if (reinstallBtn) reinstallBtn.disabled = true;

  if (uninstallFirst) {
    const current = installedPackage || (await probeInstalledPackage());
    if (current) await uninstallInstalledPackage();
    else log("Удалять нечего — JePlay уже отсутствует.", "dim");
  }

  const { version, buffer } = await fetchLatestRelease();
  await pushAndInstall(buffer);
  await grantPermissions();

  const check = await shell(`pm path ${PACKAGE_ID} 2>&1 || true`);
  log(check.trim() || "(пакет не найден после установки)");
  if (!/package:/i.test(check)) {
    throw new Error("Пакет ru.jeplay.app не найден после установки");
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

  setStatus("Готово", "ok");
  const softApHint = softAp
    ? ` SoftAP «${softAp.ssid}» записан в JePlay — откройте приложение на ГУ.`
    : " SoftAP с ГУ не удалось прочитать — пароль введите в JePlay с iPhone.";
  const modeHint = uninstallFirst ? "переустановлен" : "установлен";
  showFinal(true, `JePlay ${version} ${modeHint}.${softApHint}`);
  log(`Успех: JePlay ${version}`, "ok");
  trackGoal("install_success", { version, uninstallFirst: Boolean(uninstallFirst) });
  await refreshInstalledPackageUi();
}

async function runInstallAction(options = {}) {
  if (busy) return;
  busy = true;
  actionBtn.disabled = true;
  if (reinstallBtn) reinstallBtn.disabled = true;
  try {
    if (!adb) await connectDevice();
    else await installLatest(options);
  } catch (error) {
    const message = String(error?.message || error);
    setStatus(message, "err");
    log(message, "err");
    if (adb) {
      showFinal(false, `Ошибка установки: ${message}`);
      await refreshInstalledPackageUi().catch(() => setConnectedUi(true));
    } else {
      hideExistingPanel();
      setConnectedUi(false);
    }
    console.error(error);
  } finally {
    busy = false;
    if (adb) {
      actionBtn.disabled = false;
      if (reinstallBtn) reinstallBtn.disabled = !installedPackage;
    } else {
      actionBtn.disabled = false;
      if (reinstallBtn) reinstallBtn.disabled = true;
    }
  }
}

$("copy-softap-ssid")?.addEventListener("click", () => {
  const text = softapSsidEl?.textContent?.trim();
  if (text) copyField(text, $("copy-softap-ssid"));
});
$("copy-softap-pass")?.addEventListener("click", () => {
  const text = softapPassEl?.textContent?.trim();
  if (text && text !== "(открытая сеть)") copyField(text, $("copy-softap-pass"));
});

actionBtn.addEventListener("click", () => runInstallAction({ uninstallFirst: false }));

reinstallBtn?.addEventListener("click", () => {
  if (!adb || !installedPackage) return;
  runInstallAction({ uninstallFirst: true });
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
  setStatus("Откройте через http://localhost или https://", "err");
  log("WebUSB не работает с file:// — поднимите локальный сервер или откройте сайт по HTTPS.", "err");
}

refreshPassword();
