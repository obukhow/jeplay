<?php

require_once __DIR__ . '/config.php';

function debug_json_response($payload, $status = 200)
{
    http_response_code((int) $status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($payload);
    exit;
}

function debug_read_json_body()
{
    static $cached = null;
    if ($cached !== null) {
        return $cached;
    }
    $raw = file_get_contents('php://input');
    if ($raw === false || $raw === '') {
        $cached = array();
        return $cached;
    }
    $data = json_decode($raw, true);
    $cached = is_array($data) ? $data : array();
    return $cached;
}

function debug_ensure_data_dir()
{
    if (!is_dir(DEBUG_DATA_DIR) && !mkdir(DEBUG_DATA_DIR, 0750, true) && !is_dir(DEBUG_DATA_DIR)) {
        debug_json_response(array('ok' => false, 'error' => 'cannot create data dir'), 500);
    }
}

function debug_session_path($id)
{
    // Filesystem-safe name: 123-456 -> 123-456.json
    return DEBUG_DATA_DIR . '/' . $id . '.json';
}

/** Normalize user/agent input to XXX-XXX. */
function debug_normalize_id($id)
{
    $digits = preg_replace('/\D+/', '', (string) $id);
    if (strlen($digits) !== 6) {
        return '';
    }
    return substr($digits, 0, 3) . '-' . substr($digits, 3, 3);
}

function debug_valid_id($id)
{
    return (bool) preg_match('/^\d{3}-\d{3}$/', (string) $id);
}

function debug_lock_path($id)
{
    return debug_session_path($id) . '.lock';
}

function debug_random_hex($bytes)
{
    if (function_exists('random_bytes')) {
        return bin2hex(random_bytes($bytes));
    }
    if (function_exists('openssl_random_pseudo_bytes')) {
        return bin2hex(openssl_random_pseudo_bytes($bytes));
    }
    $out = '';
    for ($i = 0; $i < $bytes; $i++) {
        $out .= chr(mt_rand(0, 255));
    }
    return bin2hex($out);
}

/** Short session id for voice/chat: 123-456 */
function debug_new_session_id()
{
    debug_ensure_data_dir();
    for ($attempt = 0; $attempt < 40; $attempt++) {
        $n = mt_rand(0, 999999);
        $id = sprintf('%03d-%03d', (int) floor($n / 1000), $n % 1000);
        if (!is_file(debug_session_path($id))) {
            return $id;
        }
    }
    return null;
}

function debug_with_session($id, $mutator)
{
    if (!debug_valid_id($id)) {
        return null;
    }
    debug_ensure_data_dir();
    $path = debug_session_path($id);
    $lockPath = debug_lock_path($id);
    $lock = fopen($lockPath, 'c+');
    if ($lock === false) {
        return null;
    }
    $session = null;
    if (!flock($lock, LOCK_EX)) {
        fclose($lock);
        return null;
    }
    if (is_file($path)) {
        $decoded = json_decode((string) file_get_contents($path), true);
        if (is_array($decoded)) {
            $session = $decoded;
        }
    }
    if (!is_array($session) || $session === array()) {
        flock($lock, LOCK_UN);
        fclose($lock);
        return null;
    }
    $session = call_user_func($mutator, $session);
    $tmp = $path . '.tmp';
    file_put_contents($tmp, json_encode($session));
    rename($tmp, $path);
    flock($lock, LOCK_UN);
    fclose($lock);
    return $session;
}

function debug_load_session($id)
{
    if (!debug_valid_id($id)) {
        return null;
    }
    $path = debug_session_path($id);
    if (!is_file($path)) {
        return null;
    }
    $decoded = json_decode((string) file_get_contents($path), true);
    return is_array($decoded) ? $decoded : null;
}

function debug_require_admin()
{
    $hdr = isset($_SERVER['HTTP_X_DEBUG_TOKEN']) ? $_SERVER['HTTP_X_DEBUG_TOKEN'] : '';
    $body = debug_read_json_body();
    $token = '';
    if (is_string($hdr) && $hdr !== '') {
        $token = $hdr;
    } elseif (isset($body['token'])) {
        $token = (string) $body['token'];
    } elseif (isset($_GET['token'])) {
        $token = (string) $_GET['token'];
    }
    if (!hash_equals(DEBUG_ADMIN_PASSWORD, $token)) {
        // hash_equals exists since PHP 5.6; hosting appears to be 5.x+.
        debug_json_response(array('ok' => false, 'error' => 'unauthorized'), 401);
    }
}

function debug_gc_sessions()
{
    debug_ensure_data_dir();
    $now = time();
    $files = glob(DEBUG_DATA_DIR . '/*.json');
    if (!$files) {
        return;
    }
    foreach ($files as $file) {
        $decoded = json_decode((string) file_get_contents($file), true);
        if (!is_array($decoded)) {
            @unlink($file);
            continue;
        }
        $last = 0;
        if (isset($decoded['lastSeen'])) {
            $last = (int) $decoded['lastSeen'];
        } elseif (isset($decoded['created'])) {
            $last = (int) $decoded['created'];
        }
        if ($last > 0 && ($now - $last) > DEBUG_SESSION_TTL) {
            @unlink($file);
            @unlink($file . '.lock');
        }
    }
}

if (!function_exists('hash_equals')) {
    function hash_equals($a, $b)
    {
        if (!is_string($a) || !is_string($b) || strlen($a) !== strlen($b)) {
            return false;
        }
        $res = 0;
        $len = strlen($a);
        for ($i = 0; $i < $len; $i++) {
            $res |= ord($a[$i]) ^ ord($b[$i]);
        }
        return $res === 0;
    }
}

if (!function_exists('http_response_code')) {
    function http_response_code($code = null)
    {
        static $current = 200;
        if ($code === null) {
            return $current;
        }
        $current = (int) $code;
        $text = array(
            200 => 'OK',
            400 => 'Bad Request',
            401 => 'Unauthorized',
            404 => 'Not Found',
            405 => 'Method Not Allowed',
            409 => 'Conflict',
            500 => 'Internal Server Error',
            504 => 'Gateway Timeout',
        );
        $msg = isset($text[$current]) ? $text[$current] : 'Unknown';
        header('HTTP/1.1 ' . $current . ' ' . $msg);
        return $current;
    }
}
