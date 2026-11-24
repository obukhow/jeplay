<?php
/**
 * Accept a diagnostic report from the WebUSB installer and store it under data/reports/.
 * POST JSON: { "text": "...", "serial": "...", "version": "...", "source": "..." }
 * Response: { "ok": true, "id": "…", "bytes": N }
 *
 * PHP 5.x compatible (same hosting as the rest of /debug).
 */
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    debug_json_response(array('ok' => false, 'error' => 'POST only'), 405);
}

debug_ensure_data_dir();

$reportsDir = DEBUG_DATA_DIR . '/reports';
if (!is_dir($reportsDir) && !mkdir($reportsDir, 0750, true) && !is_dir($reportsDir)) {
    debug_json_response(array('ok' => false, 'error' => 'cannot create reports dir'), 500);
}

$body = debug_read_json_body();
$text = isset($body['text']) ? (string) $body['text'] : '';
if ($text === '' && isset($_POST['text'])) {
    $text = (string) $_POST['text'];
}
$text = str_replace("\0", '', $text);
$len = strlen($text);
if ($len < 32) {
    debug_json_response(array('ok' => false, 'error' => 'report too short'), 400);
}
if ($len > 8 * 1024 * 1024) {
    debug_json_response(array('ok' => false, 'error' => 'report too large'), 413);
}

$serial = substr(isset($body['serial']) ? (string) $body['serial'] : '', 0, 120);
$version = substr(isset($body['version']) ? (string) $body['version'] : '', 0, 40);
$source = substr(isset($body['source']) ? (string) $body['source'] : 'installer', 0, 40);
$sessionRaw = isset($body['session']) ? (string) $body['session'] : '';
$session = debug_normalize_id($sessionRaw);
$analysis = isset($body['analysis']) ? substr((string) $body['analysis'], 0, 4000) : '';
$now = time();
$id = date('Ymd-His', $now) . '-' . substr(debug_random_hex(4), 0, 8);

$targetDir = $reportsDir;
if ($session !== '' && debug_valid_id($session)) {
    $targetDir = $reportsDir . '/' . $session;
    if (!is_dir($targetDir) && !mkdir($targetDir, 0750, true) && !is_dir($targetDir)) {
        debug_json_response(array('ok' => false, 'error' => 'cannot create session reports dir'), 500);
    }
}

$header = "JePlay diagnostic upload\n"
    . "id=$id\n"
    . "session=$session\n"
    . "at=" . gmdate('c', $now) . "\n"
    . "serial=$serial\n"
    . "version=$version\n"
    . "source=$source\n"
    . "ip=" . (isset($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '') . "\n"
    . "ua=" . substr(isset($_SERVER['HTTP_USER_AGENT']) ? $_SERVER['HTTP_USER_AGENT'] : '', 0, 200) . "\n";
if ($analysis !== '') {
    $header .= "analysis=<<END_ANALYSIS\n" . $analysis . "\nEND_ANALYSIS\n";
}
$header .= "----\n";

$path = $targetDir . '/' . $id . '.txt';
$tmp = $path . '.tmp';
if (file_put_contents($tmp, $header . $text) === false) {
    debug_json_response(array('ok' => false, 'error' => 'write failed'), 500);
}
rename($tmp, $path);

// Keep the newest 80 reports in the flat folder (session subdirs are small).
$files = glob($reportsDir . '/*.txt');
if (is_array($files) && count($files) > 80) {
    usort($files, function ($a, $b) {
        return filemtime($a) - filemtime($b);
    });
    $drop = count($files) - 80;
    for ($i = 0; $i < $drop; $i++) {
        @unlink($files[$i]);
    }
}

debug_json_response(array(
    'ok' => true,
    'id' => $id,
    'session' => $session,
    'bytes' => $len,
    'tellDeveloper' => $session !== '' ? $session : $id,
));
