<?php

require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    debug_json_response(array('ok' => false, 'error' => 'POST only'), 405);
}

$body = debug_read_json_body();
$id = debug_normalize_id(isset($body['id']) ? $body['id'] : '');
$cmdId = isset($body['cmdId']) ? (string) $body['cmdId'] : '';
$cmd = isset($body['cmd']) ? (string) $body['cmd'] : '';
$output = isset($body['output']) ? (string) $body['output'] : '';
$error = array_key_exists('error', $body) ? $body['error'] : null;
if ($error !== null) {
    $error = (string) $error;
}

if (!debug_valid_id($id) || $cmdId === '') {
    debug_json_response(array('ok' => false, 'error' => 'bad request'), 400);
}

if (strlen($output) > 200000) {
    $output = substr($output, 0, 200000) . "\n…[truncated]";
}

$session = debug_with_session($id, function ($session) use ($cmdId, $cmd, $output, $error) {
    $session['lastSeen'] = time();
    $session['online'] = true;
    $results = isset($session['results']) && is_array($session['results']) ? $session['results'] : array();
    $results[] = array(
        'id' => $cmdId,
        'cmd' => substr($cmd, 0, 2000),
        'output' => $output,
        'error' => $error,
        'at' => time(),
    );
    if (count($results) > DEBUG_MAX_RESULTS) {
        $results = array_slice($results, -DEBUG_MAX_RESULTS);
    }
    $session['results'] = $results;
    return $session;
});

if ($session === null) {
    debug_json_response(array('ok' => false, 'error' => 'unknown session'), 404);
}

debug_json_response(array('ok' => true));
