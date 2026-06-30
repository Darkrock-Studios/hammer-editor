#!/usr/bin/env bash
# Disable the simulator's "Connect Hardware Keyboard" so the *software* keyboard shows during UI
# tests. Compose-on-iOS text fields need the software keyboard up for XCUITest's typeText to land;
# with a hardware keyboard "connected" the soft keyboard stays hidden and typeText fails with
# "Neither element nor any descendant has keyboard focus".
#
# Safe to run repeatedly. Run before `xcodebuild test`, then (re)launch the simulator so the
# preference is picked up.
set -euo pipefail

PLIST="$HOME/Library/Preferences/com.apple.iphonesimulator.plist"

# Global default applied to freshly-created device windows.
defaults write com.apple.iphonesimulator ConnectHardwareKeyboard -bool false || true

# Per-device override (Simulator persists this keyed by UDID).
/usr/libexec/PlistBuddy -c "Add :DevicePreferences dict" "$PLIST" 2>/dev/null || true
for udid in $(xcrun simctl list devices --json \
  | /usr/bin/python3 -c "import json,sys;[print(x['udid']) for v in json.load(sys.stdin)['devices'].values() for x in v]"); do
  /usr/libexec/PlistBuddy -c "Add :DevicePreferences:$udid dict" "$PLIST" 2>/dev/null || true
  /usr/libexec/PlistBuddy -c "Set :DevicePreferences:$udid:ConnectHardwareKeyboard false" "$PLIST" 2>/dev/null \
    || /usr/libexec/PlistBuddy -c "Add :DevicePreferences:$udid:ConnectHardwareKeyboard bool false" "$PLIST" 2>/dev/null || true
done

echo "Disabled hardware keyboard for all simulators."
