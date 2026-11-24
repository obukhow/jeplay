---
name: jeplay-release
description: >-
  Build, publish, and tag a JePlay release. Use when the user asks to release,
  deploy, publish, or ship a new JePlay or DiPlay version, bump versionName or
  versionCode, upload an APK to jeplay.ru, or create a v0.2.21.N tag.
---

# JePlay release

Work from the DiPlay repo root (`…/carplay-app/DiPlay`). Follow [deploy.md](../../../jaecoo/deploy.md) in order: bump, signed assemble, checksums, FTP, then commit, annotated tag, and push.

## Rules

- Source `/Users/roomine/projects/carplay-app/.local/diplay-release/env.sh`. Never print or commit `JEPLAY_FTP_PASS`, keystore passwords, `env.sh`, or `*.jks`.
- Do not `git add` `*.apk`. Publish APKs only over FTP.
- Read the current `versionName` / `versionCode` from `mobile/build.gradle.kts`. The numbers in `deploy.md` are examples. Increment the JePlay patch (the fourth version digit and the last two digits of `versionCode`) by 1. Keep the first three version digits and the upstream part of `versionCode` unless this release tracks a new DiPlay upstream.
- Write that same version string to both `jaecoo/site/version.txt` files and the README examples.
- `aapt dump badging` must show `name='ru.jeplay.app'` and the new version before upload.
- After FTP, `https://jeplay.ru/version.txt` must equal the new version and the live APK `Content-Length` must equal the local file. Do not tag if either check fails.
- Commit subject: `v0.2.21.N - <why this release exists>`.
- `origin/HEAD` is `master`. Fast-forward `master` to the release commit and push `master` plus the annotated tag. Do not force-push.
- If `jaecoo/site` HTML, `assets/`, `debug/`, or `en/` changed, upload those paths to the same FTP root.
