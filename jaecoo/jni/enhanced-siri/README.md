# Desay Enhanced Siri natives (Jaecoo)

JePlay loads these libraries for continuous «Hey Siri» / «Привет, Siri».
They are **not** committed to git (large OEM binaries).

## Populate for a device build

From the repo root (or any machine that has the Desay CarPlay extract):

```bash
./DiPlay/jaecoo/jni/enhanced-siri/copy-from-desay.sh
```

Or copy manually into `arm64-v8a/`:

| File | Role |
|------|------|
| `libenhancesiri_jni.so` | JNI entry (required) |
| `libissmvw.so` | iFlytek MVW wake-word |
| `libwake_word_evaluate.so` | wake-word evaluate |
| `libSpWord.so` | keyword models helper |
| `libissauth.so` | iFlytek auth (if present) |
| `libw_ivw.so` / `libw_ivw_proton.so` | optional IVW |

Source in this workspace: `DesaySVCarPlay/resources/lib/arm64-v8a/`.

## On-device models

Wake-word resources stay on the head unit (not in the APK):

- `/product/etc/Res/MVWRes/FirstRes`
- `/data_extra/carplay/Res/MVWRes/FirstRes`

Without that directory the setting stays inert (`models=missing` in the description).

## Runtime fallback

If the APK has no jniLibs copy, JePlay also tries absolute paths under
`/product/lib64`, `/system/lib64`, and leftover Desay CarPlay install dirs.
