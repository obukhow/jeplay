<?php

require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET' && $_SERVER['REQUEST_METHOD'] !== 'POST') {
    debug_json_response(array('ok' => false, 'error' => 'GET/POST only'), 405);
}

debug_require_admin();
debug_gc_sessions();
debug_ensure_data_dir();

$id = isset($_GET['id']) ? debug_normalize_id($_GET['id']) : '';
if ($id !== '') {
    $session = debug_load_session($id);
    if ($session === null) {
        debug_json_response(array('ok' => false, 'error' => 'unknown session'), 404);
    }
    debug_json_response(array('ok' => true, 'session' => $session));
}

$now = time();
$list = array();
$files = glob(DEBUG_DATA_DIR . '/*.json');
if ($files) {
    foreach ($files as $file) {
        $decoded = json_decode((string) file_get_contents($file), true);
        if (!is_array($decoded)) {
            continue;
        }
        $lastSeen = isset($decoded['lastSeen']) ? (int) $decoded['lastSeen'] : 0;
        $sid = isset($decoded['id']) ? (string) $decoded['id'] : basename($file, '.json');
        $list[] = array(
            'id' => $sid,
            'serial' => isset($decoded['serial']) ? (string) $decoded['serial'] : '',
            'created' => isset($decoded['created']) ? (int) $decoded['created'] : 0,
            'lastSeen' => $lastSeen,
            'online' => !empty($decoded['online']) && ($now - $lastSeen) < 20,
            'queue' => isset($decoded['queue']) && is_array($decoded['queue']) ? count($decoded['queue']) : 0,
            'results' => isset($decoded['results']) && is_array($decoded['results']) ? count($decoded['results']) : 0,
            'lang' => isset($decoded['lang']) ? (string) $decoded['lang'] : '',
            'page' => isset($decoded['page']) ? (string) $decoded['page'] : '',
        );
    }
}

usort($list, function ($a, $b) {
    if ($a['lastSeen'] == $b['lastSeen']) {
        return 0;
    }
    return ($a['lastSeen'] < $b['lastSeen']) ? 1 : -1;
});

debug_json_response(array('ok' => true, 'sessions' => $list));
