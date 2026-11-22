<?php

require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    debug_json_response(array('ok' => false, 'error' => 'POST only'), 405);
}

debug_gc_sessions();
debug_ensure_data_dir();

$body = debug_read_json_body();
$id = debug_new_session_id();
if ($id === null) {
    debug_json_response(array('ok' => false, 'error' => 'no free session id'), 503);
}
$now = time();

$session = array(
    'id' => $id,
    'created' => $now,
    'lastSeen' => $now,
    'online' => true,
    'serial' => substr(isset($body['serial']) ? (string) $body['serial'] : '', 0, 120),
    'userAgent' => substr(isset($_SERVER['HTTP_USER_AGENT']) ? (string) $_SERVER['HTTP_USER_AGENT'] : '', 0, 240),
    'lang' => substr(isset($body['lang']) ? (string) $body['lang'] : '', 0, 8),
    'page' => substr(isset($body['page']) ? (string) $body['page'] : '', 0, 200),
    'note' => '',
    'queue' => array(),
    'results' => array(),
);

$path = debug_session_path($id);
$tmp = $path . '.tmp';
file_put_contents($tmp, json_encode($session));
rename($tmp, $path);

debug_json_response(array('ok' => true, 'id' => $id));
