<?php

require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    debug_json_response(array('ok' => false, 'error' => 'POST only'), 405);
}

$body = debug_read_json_body();
$id = debug_normalize_id(isset($body['id']) ? $body['id'] : '');
if (!debug_valid_id($id)) {
    debug_json_response(array('ok' => false, 'error' => 'bad id'), 400);
}

$session = debug_with_session($id, function ($session) {
    $session['online'] = false;
    $session['lastSeen'] = time();
    return $session;
});

if ($session === null) {
    debug_json_response(array('ok' => false, 'error' => 'unknown session'), 404);
}

debug_json_response(array('ok' => true));
