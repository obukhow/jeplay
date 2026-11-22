<?php

require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    debug_json_response(array('ok' => false, 'error' => 'POST only'), 405);
}

debug_require_admin();

$body = debug_read_json_body();
$id = debug_normalize_id(isset($body['id']) ? $body['id'] : '');
$cmd = trim(isset($body['cmd']) ? (string) $body['cmd'] : '');

if (!debug_valid_id($id) || $cmd === '') {
    debug_json_response(array('ok' => false, 'error' => 'bad request'), 400);
}
if (strlen($cmd) > 4000) {
    debug_json_response(array('ok' => false, 'error' => 'command too long'), 400);
}

$cmdId = debug_random_hex(8);
$session = debug_with_session($id, function ($session) use ($cmdId, $cmd) {
    $queue = isset($session['queue']) && is_array($session['queue']) ? $session['queue'] : array();
    $queue[] = array(
        'id' => $cmdId,
        'cmd' => $cmd,
        'queuedAt' => time(),
    );
    $session['queue'] = $queue;
    return $session;
});

if ($session === null) {
    debug_json_response(array('ok' => false, 'error' => 'unknown session'), 404);
}

debug_json_response(array('ok' => true, 'cmdId' => $cmdId));
