#!/usr/bin/env bash
# Copy OEM Enhanced Siri .so files into the JePlay jniLibs folder.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
SRC="$ROOT/DesaySVCarPlay/resources/lib/arm64-v8a"
DST="$(cd "$(dirname "$0")" && pwd)/arm64-v8a"
mkdir -p "$DST"
LIBS=(
  libenhancesiri_jni.so
  libissmvw.so
  libwake_word_evaluate.so
  libSpWord.so
  libissauth.so
  libw_ivw.so
  libw_ivw_proton.so
)
for lib in "${LIBS[@]}"; do
  if [[ -f "$SRC/$lib" ]]; then
    cp -f "$SRC/$lib" "$DST/$lib"
    echo "copied $lib ($(wc -c <"$DST/$lib") bytes)"
  else
    echo "missing $SRC/$lib (optional unless enhancesiri_jni)" >&2
  fi
done
echo "Done → $DST"
