# JePlay release (build → site → git tag)

Release a new JePlay patch (`0.2.21.N` / `versionCode = 40NN`), publish APK +
checksums to [jeplay.ru](https://jeplay.ru), then tag and push.

Secrets live **outside git** in:

```bash
source /Users/roomine/projects/carplay-app/.local/diplay-release/env.sh
```

That file must export: `JAVA_HOME`, `ANDROID_HOME`, `DIPLAY_AUTH_ASSETS_DIR`,
`ANDROID_KEYSTORE_*`, `ANDROID_KEY_*`, and FTP:

| Variable | Example |
|----------|---------|
| `JEPLAY_FTP_HOST` | `p55796.ftp.ihc.ru` |
| `JEPLAY_FTP_USER` | `p55796` |
| `JEPLAY_FTP_PASS` | *(local only)* |
| `JEPLAY_FTP_ROOT` | `www/jeplay.ru` |

Work from the DiPlay repo root (`…/carplay-app/DiPlay`).

---

## 1. Bump version

Edit `mobile/build.gradle.kts`:

```kotlin
versionCode = 4034          // upstream DiPlay code * 100 + JePlay patch
versionName = "0.2.21.34"   // first three = DiPlay; fourth = JePlay-only
```

Also set the same string in:

- `jaecoo/site/version.txt`
- `jaecoo/site/download/version.txt`
- examples in `jaecoo/README.md` and `jaecoo/site/download/README.md` (optional but keeps docs honest)

---

## 2. Build signed release APK

```bash
source /Users/roomine/projects/carplay-app/.local/diplay-release/env.sh
cd /Users/roomine/projects/carplay-app/DiPlay

# If Kotlin reports weird “Unresolved reference” noise, clear common caches first:
# rm -rf common/build/kotlin

./gradlew :mobile:assembleRelease
```

Artifact:

```text
mobile/build/outputs/apk/release/JePlay-<versionName>-release.apk
```

Check:

```bash
aapt dump badging mobile/build/outputs/apk/release/JePlay-*-release.apk | head -3
# expect: name='ru.jeplay.app' versionCode='…' versionName='…'
```

---

## 3. Stage download/ + checksums

```bash
VER=$(tr -d '[:space:]' < jaecoo/site/version.txt)
SRC=mobile/build/outputs/apk/release/JePlay-${VER}-release.apk
DEST=jaecoo/site/download/JePlay-${VER}.apk
cp -f "$SRC" "$DEST"

HASH=$(shasum -a 256 "$DEST" | awk '{print $1}')
{
  echo "$HASH  JePlay-${VER}.apk"
  grep -v "JePlay-${VER}.apk" jaecoo/site/download/SHA256SUMS.txt || true
} > jaecoo/site/download/SHA256SUMS.txt.new
mv jaecoo/site/download/SHA256SUMS.txt.new jaecoo/site/download/SHA256SUMS.txt
echo "$HASH  JePlay-${VER}.apk" > jaecoo/site/download/SHA.txt
cp -f jaecoo/site/version.txt jaecoo/site/download/version.txt
```

Layout on the server:

```text
jeplay.ru/version.txt
jeplay.ru/download/version.txt
jeplay.ru/download/JePlay-<ver>.apk
jeplay.ru/download/SHA256SUMS.txt
jeplay.ru/download/SHA.txt          # optional mirror of the latest line
```

---

## 4. Upload over FTP (IHC)

```bash
source /Users/roomine/projects/carplay-app/.local/diplay-release/env.sh
SITE=jaecoo/site
AUTH="$JEPLAY_FTP_USER:$JEPLAY_FTP_PASS"
BASE="ftp://$JEPLAY_FTP_HOST/$JEPLAY_FTP_ROOT"
VER=$(tr -d '[:space:]' < "$SITE/version.txt")

curl -sS --user "$AUTH" -T "$SITE/version.txt"                "$BASE/version.txt"
curl -sS --user "$AUTH" -T "$SITE/download/version.txt"       "$BASE/download/version.txt"
curl -sS --user "$AUTH" -T "$SITE/download/SHA256SUMS.txt"    "$BASE/download/SHA256SUMS.txt"
curl -sS --user "$AUTH" -T "$SITE/download/SHA.txt"           "$BASE/download/SHA.txt"
curl -sS --user "$AUTH" -T "$SITE/download/JePlay-${VER}.apk" "$BASE/download/JePlay-${VER}.apk"
```

Verify:

```bash
curl -sS https://jeplay.ru/version.txt; echo
curl -sS https://jeplay.ru/download/SHA256SUMS.txt | head -3
curl -sSI "https://jeplay.ru/download/JePlay-${VER}.apk" | rg -i 'HTTP/|content-length'
```

Local and remote `Content-Length` must match.

---

## 5. Git commit, tag, push

Use a short message that states the version and why (same style as
`v0.2.21.33 - Fix hud line assist`).

```bash
VER=$(tr -d '[:space:]' < jaecoo/site/version.txt)
TAG="v${VER}"
MSG="${TAG} - <one-line reason>"

git status
git add mobile/build.gradle.kts \
  jaecoo/site/version.txt \
  jaecoo/site/download/version.txt \
  jaecoo/site/download/SHA256SUMS.txt \
  jaecoo/site/download/SHA.txt \
  jaecoo/README.md \
  jaecoo/site/download/README.md \
  # plus any code/strings that ship in this release
git add -u   # or add specific paths; do NOT add *.apk / keystore / env.sh

# APKs are large — keep them out of git (publish only via FTP).
# If download/*.apk is tracked by mistake: git rm --cached jaecoo/site/download/*.apk

git commit -m "$MSG"
git tag -a "$TAG" -m "$MSG"

# Push branch + tag (adjust branch name if not master)
git push -u origin HEAD
git push origin "$TAG"
```

Remote: `git@github.com:obukhow/jeplay.git`.

---

## Checklist

- [ ] `versionCode` / `versionName` bumped in `mobile/build.gradle.kts`
- [ ] Both `version.txt` files match
- [ ] Release APK signed; `aapt` shows expected package/version
- [ ] `SHA256SUMS.txt` has the new APK first
- [ ] Live `https://jeplay.ru/version.txt` shows the new version
- [ ] Live APK size matches local file
- [ ] Annotated tag `v…` pushed to `origin`

---

## Notes

- In-app updater reads `https://jeplay.ru/version.txt`, then downloads
  `https://jeplay.ru/download/JePlay-<version>.apk` and checks `SHA256SUMS.txt`
  when present.
- SoftAP / installer / site HTML changes may need extra FTP uploads under
  `assets/`, `debug/`, `en/` — same `BASE` path as above.
- Do not commit `release.jks`, `env.sh`, or FTP passwords.
