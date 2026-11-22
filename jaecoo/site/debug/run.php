<?php

/**
 * Agent/admin API: queue one shell command and wait for the browser to execute it.
 *
 * POST JSON:
 *   { "id": "123-456", "cmd": "ls -la /data/local/tmp", "timeout": 45, "token": "..." }
 * Auth: X-Debug-Token header, or token in JSON / ?token=
 */
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    debug_json_response(array('ok' => false, 'error' => 'POST only'), 405);
}

debug_require_admin();

$body = debug_read_json_body();
$id = debug_normalize_id(isset($body['id']) ? $body['id'] : '');
$cmd = trim(isset($body['cmd']) ? (string) $body['cmd'] : '');
$timeout = isset($body['timeout']) ? (int) $body['timeout'] : 45;
if ($timeout < 5) {
    $timeout = 5;
}
if ($timeout > 120) {
    $timeout = 120;
}

if (!debug_valid_id($id) || $cmd === '') {
    debug_json_response(array('ok' => false, 'error' => 'bad request'), 400);
}
if (strlen($cmd) > 4000) {
    debug_json_response(array('ok' => false, 'error' => 'command too long'), 400);
}

@set_time_limit($timeout + 15);

$session = debug_load_session($id);
if ($session === null) {
    debug_json_response(array('ok' => false, 'error' => 'unknown session'), 404);
}

$now = time();
$lastSeen = isset($session['lastSeen']) ? (int) $session['lastSeen'] : 0;
$online = !empty($session['online']) && ($now - $lastSeen) < 20;
if (!$online) {
    debug_json_response(array(
        'ok' => false,
        'error' => 'offline',
        'hint' => 'Browser is not polling. User must keep the install page open with an active Connect.',
        'lastSeen' => $lastSeen,
    ), 409);
}

$cmdId = debug_random_hex(8);
$queued = debug_with_session($id, function ($session) use ($cmdId, $cmd) {
    $queue = isset($session['queue']) && is_array($session['queue']) ? $session['queue'] : array();
    $queue[] = array(
        'id' => $cmdId,
        'cmd' => $cmd,
        'queuedAt' => time(),
    );
    $session['queue'] = $queue;
    return $session;
});

if ($queued === null) {
    debug_json_response(array('ok' => false, 'error' => 'unknown session'), 404);
}

$deadline = time() + $timeout;
while (time() <= $deadline) {
    $current = debug_load_session($id);
    if ($current === null) {
        debug_json_response(array('ok' => false, 'error' => 'session disappeared', 'cmdId' => $cmdId), 404);
    }
    $results = isset($current['results']) && is_array($current['results']) ? $current['results'] : array();
    foreach ($results as $result) {
        if (!is_array($result) || !isset($result['id']) || (string) $result['id'] !== $cmdId) {
            continue;
        }
        debug_json_response(array(
            'ok' => true,
            'id' => $id,
            'cmdId' => $cmdId,
            'cmd' => isset($result['cmd']) ? (string) $result['cmd'] : $cmd,
            'output' => isset($result['output']) ? (string) $result['output'] : '',
            'error' => array_key_exists('error', $result) ? $result['error'] : null,
            'at' => isset($result['at']) ? (int) $result['at'] : time(),
        ));
    }
    usleep(400000);
}

debug_json_response(array(
    'ok' => false,
    'error' => 'timeout',
    'cmdId' => $cmdId,
    'hint' => 'Command was queued but no result arrived. Install may be busy, or the tab lost ADB.',
), 504);
