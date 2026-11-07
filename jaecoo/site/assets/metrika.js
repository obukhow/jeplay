/**
 * Яндекс.Метрика для jeplay.ru.
 * Подставьте номер счётчика из https://metrika.yandex.ru/
 *
 * Воронка (Отчёты → Конверсии → Воронки):
 * 1. site_visit      — любой визит (или просмотр страницы)
 * 2. install_page    — открыли /install.html
 * 3. connect_click   — нажали Connect
 * 4. install_click   — нажали Установить / Обновить
 * 5. install_success — установка завершилась успешно
 *
 * В Метрике создайте цели типа «JavaScript-событие» с теми же идентификаторами.
 */
(function () {
  var YM_COUNTER_ID = 114092842;

  window.JEPLAY_YM_COUNTER_ID = YM_COUNTER_ID;

  window.jeplayYmGoal = function (target, params) {
    if (!YM_COUNTER_ID || typeof window.ym !== "function") return;
    try {
      window.ym(YM_COUNTER_ID, "reachGoal", target, params);
    } catch (_) {
      /* ignore analytics errors */
    }
  };

  if (!YM_COUNTER_ID) return;

  (function (m, e, t, r, i, k, a) {
    m[i] =
      m[i] ||
      function () {
        (m[i].a = m[i].a || []).push(arguments);
      };
    m[i].l = 1 * new Date();
    for (var j = 0; j < document.scripts.length; j++) {
      if (document.scripts[j].src === r) return;
    }
    k = e.createElement(t);
    a = e.getElementsByTagName(t)[0];
    k.async = 1;
    k.src = r;
    a.parentNode.insertBefore(k, a);
  })(window, document, "script", "https://mc.yandex.ru/metrika/tag.js", "ym");

  window.ym(YM_COUNTER_ID, "init", {
    clickmap: true,
    trackLinks: true,
    accurateTrackBounce: true,
    webvisor: true,
  });

  // Шаг 1 воронки: явная цель на любой странице (удобно для составной цели / воронки).
  window.jeplayYmGoal("site_visit");

  if (/install\.html(?:$|\?|#)/i.test(location.pathname + location.search + location.hash) ||
      /\/install\/?$/i.test(location.pathname)) {
    window.jeplayYmGoal("install_page");
  }
})();
