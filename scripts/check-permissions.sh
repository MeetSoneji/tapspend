#!/usr/bin/env bash
# Fails if the merged release manifest requests anything outside the allowlist.
# Above all: INTERNET and any location permission must never appear. (section 7 / 8 of the plan)
set -euo pipefail

MANIFEST="${1:-app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml}"
[ -f "$MANIFEST" ] || { echo "Merged manifest not found: $MANIFEST (run ./gradlew :app:assembleRelease first)"; exit 2; }

ALLOWED=(
  "android.permission.USE_BIOMETRIC"
  "android.permission.USE_FINGERPRINT"          # added by androidx.biometric
  "android.permission.RECEIVE_BOOT_COMPLETED"   # midnight widget reset + WorkManager (Glance)
  "android.permission.WAKE_LOCK"                # WorkManager (Glance widget updates)
  "android.permission.ACCESS_NETWORK_STATE"     # WorkManager; cannot send data
)

status=0
while IFS= read -r perm; do
  [ -z "$perm" ] && continue
  case "$perm" in
    *.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) continue ;; # AndroidX receiver-protection permission, app-scoped
  esac
  ok=0
  for a in "${ALLOWED[@]}"; do [ "$perm" = "$a" ] && ok=1; done
  if [ $ok -eq 0 ]; then echo "FORBIDDEN permission in merged manifest: $perm"; status=1; fi
done < <(grep -oE '<uses-permission[^>]*android:name="[^"]+"' "$MANIFEST" | sed -E 's/.*android:name="//; s/"$//' | sort -u)

if grep -qE 'android.permission.(INTERNET|ACCESS_(FINE|COARSE|BACKGROUND)_LOCATION|FOREGROUND_SERVICE)' "$MANIFEST"; then
  echo "INTERNET / location / foreground-service permission present"; status=1
fi
if grep -q 'SystemForegroundService' "$MANIFEST"; then echo "SystemForegroundService present"; status=1; fi

[ $status -eq 0 ] && echo "Permission allowlist OK: no INTERNET, no location, no foreground service."
exit $status
