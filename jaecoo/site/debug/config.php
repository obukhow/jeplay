<?php
/**
 * Shared secret for admin UI + agent API (run.php / sessions.php / command.php).
 *
 * Prefer override file (gitignored):
 *   debug/config.local.php
 *   <?php define('DEBUG_ADMIN_PASSWORD', 'real-secret');
 */
if (is_file(__DIR__ . '/config.local.php')) {
    require_once __DIR__ . '/config.local.php';
}

if (!defined('DEBUG_ADMIN_PASSWORD')) {
    define('DEBUG_ADMIN_PASSWORD', 'change-me-jeplay-debug');
}

if (!defined('DEBUG_DATA_DIR')) {
    define('DEBUG_DATA_DIR', __DIR__ . '/data');
}

if (!defined('DEBUG_SESSION_TTL')) {
    define('DEBUG_SESSION_TTL', 6 * 60 * 60);
}

if (!defined('DEBUG_MAX_RESULTS')) {
    define('DEBUG_MAX_RESULTS', 80);
}
