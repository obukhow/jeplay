<?php
require_once __DIR__ . '/config.php';
?><!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>JePlay remote ADB debug</title>
  <style>
    :root { color-scheme: dark; font-family: ui-sans-serif, system-ui, sans-serif; }
    body { margin: 0; background: #0a121c; color: #e8f1fa; }
    main { max-width: 1100px; margin: 0 auto; padding: 24px 16px 48px; }
    h1 { font-weight: 650; font-size: 1.4rem; margin: 0 0 16px; }
    .row { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
    input, textarea, button, select {
      background: #121c28; color: #e8f1fa; border: 1px solid #2a3b50;
      border-radius: 10px; padding: 10px 12px; font: inherit;
    }
    input[type="password"], input[type="text"] { min-width: 220px; }
    button { cursor: pointer; background: #1f6f66; border-color: #2a9a8d; }
    button.secondary { background: #182433; }
    table { width: 100%; border-collapse: collapse; font-size: 0.92rem; }
    th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #243447; vertical-align: top; }
    .ok { color: #3ecf8e; }
    .off { color: #8fa6bd; }
    pre {
      white-space: pre-wrap; word-break: break-word; background: #060b12;
      border: 1px solid #243447; border-radius: 10px; padding: 12px; max-height: 420px; overflow: auto;
      font: 12px/1.45 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    }
    .panel { border: 1px solid #243447; border-radius: 12px; padding: 14px; margin-top: 16px; background: rgba(255,255,255,0.02); }
    .muted { color: #8fa6bd; font-size: 0.88rem; }
    .id { font-family: ui-monospace, monospace; font-size: 0.85rem; }
  </style>
</head>
<body>
<main>
  <h1>JePlay remote ADB debug</h1>
  <p class="muted">
    Сессии появляются после Connect. Пароль — из <code>debug/config.php</code> / <code>config.local.php</code>.
    Agent API: <code>POST /debug/run.php</code> · docs: <a href="api.php">api.php</a>
  </p>

  <div class="row" id="auth-row">
    <input type="password" id="token" placeholder="Admin password" />
    <button type="button" id="save-token">Сохранить</button>
    <button type="button" class="secondary" id="refresh">Обновить список</button>
  </div>

  <div class="panel">
    <h2 style="margin:0 0 10px;font-size:1.05rem">Активные сессии</h2>
    <table>
      <thead>
        <tr><th>ID</th><th>Serial</th><th>Status</th><th>Seen</th><th></th></tr>
      </thead>
      <tbody id="sessions"></tbody>
    </table>
  </div>

  <div class="panel" id="session-panel" hidden>
    <div class="row">
      <strong>Session</strong>
      <span class="id" id="current-id"></span>
      <button type="button" class="secondary" id="reload-session">Обновить лог</button>
    </div>
    <div class="row">
      <input type="text" id="cmd" style="flex:1;min-width:260px" placeholder="shell command, e.g. ls -la /data/local/tmp" />
      <button type="button" id="send">Отправить</button>
    </div>
    <p class="muted">Команда выполнится на ГУ через браузер пользователя (poll ~2с).</p>
    <div id="results"></div>
  </div>
</main>
<script>
const TOKEN_KEY = "jeplay-debug-token";
const tokenEl = document.getElementById("token");
const sessionsEl = document.getElementById("sessions");
const panel = document.getElementById("session-panel");
const currentIdEl = document.getElementById("current-id");
const resultsEl = document.getElementById("results");
const cmdEl = document.getElementById("cmd");
let currentId = null;

tokenEl.value = localStorage.getItem(TOKEN_KEY) || "";

function token() {
  return tokenEl.value.trim();
}

async function api(path, options = {}) {
  const headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
  if (token()) headers["X-Debug-Token"] = token();
  const res = await fetch(path, Object.assign({}, options, { headers }));
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.ok === false) {
    throw new Error(data.error || ("HTTP " + res.status));
  }
  return data;
}

function fmtTime(ts) {
  if (!ts) return "—";
  return new Date(ts * 1000).toLocaleString();
}

async function loadSessions() {
  const data = await api("sessions.php");
  sessionsEl.innerHTML = "";
  for (const s of data.sessions || []) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="id">${s.id}</td>
      <td>${s.serial || "—"}</td>
      <td class="${s.online ? "ok" : "off"}">${s.online ? "online" : "offline"}</td>
      <td>${fmtTime(s.lastSeen)}<div class="muted">q:${s.queue} r:${s.results}</div></td>
      <td><button type="button" data-id="${s.id}">Открыть</button></td>`;
    tr.querySelector("button").onclick = () => openSession(s.id);
    sessionsEl.appendChild(tr);
  }
  if (!(data.sessions || []).length) {
    sessionsEl.innerHTML = `<tr><td colspan="5" class="muted">Пока нет сессий</td></tr>`;
  }
}

async function openSession(id) {
  currentId = id;
  currentIdEl.textContent = id;
  panel.hidden = false;
  await reloadSession();
}

async function reloadSession() {
  if (!currentId) return;
  const data = await api("sessions.php?id=" + encodeURIComponent(currentId));
  const session = data.session;
  const results = (session.results || []).slice().reverse();
  resultsEl.innerHTML = results.map((r) => {
    const err = r.error ? `<div style="color:#ff6b6b">error: ${escapeHtml(r.error)}</div>` : "";
    return `<div style="margin-bottom:14px">
      <div class="muted">${fmtTime(r.at)} · <span class="id">${escapeHtml(r.id)}</span></div>
      <div><code>${escapeHtml(r.cmd)}</code></div>
      ${err}
      <pre>${escapeHtml(r.output || "")}</pre>
    </div>`;
  }).join("") || `<p class="muted">Ответов пока нет</p>`;
}

function escapeHtml(s) {
  return String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

document.getElementById("save-token").onclick = () => {
  localStorage.setItem(TOKEN_KEY, token());
  loadSessions().catch((e) => alert(e.message));
};
document.getElementById("refresh").onclick = () => loadSessions().catch((e) => alert(e.message));
document.getElementById("reload-session").onclick = () => reloadSession().catch((e) => alert(e.message));
document.getElementById("send").onclick = async () => {
  try {
    if (!currentId) return;
    const cmd = cmdEl.value.trim();
    if (!cmd) return;
    await api("command.php", { method: "POST", body: JSON.stringify({ id: currentId, cmd }) });
    cmdEl.value = "";
    setTimeout(() => reloadSession().catch(() => {}), 2500);
  } catch (e) {
    alert(e.message);
  }
};

if (token()) {
  loadSessions().catch((e) => alert(e.message));
}
setInterval(() => {
  if (!token()) return;
  loadSessions().catch(() => {});
  if (currentId) reloadSession().catch(() => {});
}, 4000);
</script>
</body>
</html>
