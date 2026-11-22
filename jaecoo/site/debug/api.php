<?php

require_once __DIR__ . '/lib.php';

debug_json_response(array(
    'ok' => true,
    'name' => 'JePlay remote ADB debug API',
    'php' => PHP_VERSION,
    'auth' => array(
        'header' => 'X-Debug-Token: <DEBUG_ADMIN_PASSWORD from config.php>',
        'or' => 'JSON/query field "token"',
    ),
    'flow' => array(
        '1. User opens install page and presses Connect',
        '2. Log shows "Remote debug ID: 123-456"',
        '3. Agent calls run.php with site URL + id + token + cmd',
        '4. Browser polls, runs shell on HU, posts result',
        '5. run.php returns output',
    ),
    'endpoints' => array(
        array(
            'method' => 'POST',
            'path' => 'run.php',
            'auth' => true,
            'body' => array('id' => '123-456', 'cmd' => 'shell command', 'timeout' => 45),
            'desc' => 'Queue command and wait for browser result (best for agents)',
        ),
        array(
            'method' => 'GET|POST',
            'path' => 'sessions.php',
            'auth' => true,
            'query' => array('id' => 'optional 123-456'),
            'desc' => 'List sessions or fetch one session with queue/results',
        ),
        array(
            'method' => 'POST',
            'path' => 'command.php',
            'auth' => true,
            'body' => array('id' => '123-456', 'cmd' => 'shell command'),
            'desc' => 'Queue only (no wait)',
        ),
        array(
            'method' => 'GET',
            'path' => 'admin.php',
            'auth' => false,
            'desc' => 'Browser UI for the same API',
        ),
    ),
    'example' => array(
        'curl' => 'curl -sS -X POST "$SITE/debug/run.php" -H "Content-Type: application/json" -H "X-Debug-Token: $TOKEN" -d \'{"id":"SESSION_ID","cmd":"ls -la /data/local/tmp","timeout":45}\'',
    ),
));
