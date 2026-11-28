# APK downloads

Publish release APKs here using the name from `../version.txt`:

```text
../version.txt          →  0.2.21.41
JePlay-0.2.21.41.apk     →  this folder
```

The install page fetches `version.txt`, then downloads `JePlay-<version>.apk` from this directory.
Optional: add `SHA256SUMS.txt` for the in-app updater.
