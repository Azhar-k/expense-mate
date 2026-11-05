#!/usr/bin/env bash
# Builds, installs and drives expense-mate on an Android emulator over adb.
# Run from the repo root in Git Bash: bash .claude/skills/run-expense-mate/driver.sh <command> [args]
#
# Setup:   boot | build | install | launch | backup | restore | errors
# Look:    shot NAME | dump | find LABEL
# Act:     tap X Y | text LABEL [N] | id RES_ID [N] | hold LABEL | type TEXT | key CODE | back
#          swipe X1 Y1 X2 Y2 | scroll up|down | sms SENDER BODY | smsdel TEXT | drawer
set -u
export MSYS_NO_PATHCONV=1   # Git Bash rewrites /sdcard/... into C:/Program Files/Git/sdcard/...

PKG=com.example.expensemate
ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
SDK=$(grep '^sdk.dir=' "$ROOT/local.properties" | cut -d= -f2- | sed -e 's/\\:/:/' -e 's/\\\\/\//g' -e 's/\\/\//g')
SDK=${ANDROID_SDK_ROOT:-$SDK}
ADB="$SDK/platform-tools/adb.exe"; [ -x "$ADB" ] || ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator.exe"; [ -x "$EMU" ] || EMU="$SDK/emulator/emulator"
AVD=${AVD:-Pixel_7_Pro}
SHOTS="$ROOT/app/build/run-shots"
BACKUP="$ROOT/app/build/run-backup/app-data.tar"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
mkdir -p "$SHOTS"

# No java on PATH here; Gradle needs JAVA_HOME. Prefer an existing one, else the JetBrains JDK.
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(ls -d "$HOME"/.jdks/jbr-* 2>/dev/null | sort | tail -1)
    export JAVA_HOME
fi

# One line per UI node: text|content-desc|resource-id (package prefix dropped)|clickable|bounds
nodes() {
    "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    "$ADB" exec-out cat /sdcard/ui.xml | tr '>' '\n' | awk '
        function attr(s, name,   m) {
            if (match(s, " " name "=\"[^\"]*\"")) { return substr(s, RSTART + length(name) + 3, RLENGTH - length(name) - 4) }
            return ""
        }
        /<node/ {
            id = attr($0, "resource-id"); sub(/com.example.expensemate:id\//, "", id)
            print attr($0, "text") "|" attr($0, "content-desc") "|" id "|" attr($0, "clickable") "|" attr($0, "bounds")
        }'
}

center() { # "[x1,y1][x2,y2]" -> "x y"
    echo "$1" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}

# Bounds of the nth node whose text or description is the label (field 1/2), or whose id is it (field 3)
bounds_of() { nodes | awk -F'|' -v l="$1" -v f="$2" '(f == "id" && $3 == l) || (f != "id" && ($1 == l || $2 == l)) {print $5}' | sed -n "${3:-1}p"; }

cmd=${1:-}; shift || true
case "$cmd" in
    boot)
        if ! "$ADB" devices | grep -q 'emulator-'; then
            ( "$EMU" -avd "$AVD" -no-snapshot-save -no-boot-anim >/dev/null 2>&1 & )
        fi
        "$ADB" wait-for-device
        for _ in $(seq 1 120); do
            [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && { echo booted; exit 0; }
            sleep 2
        done
        echo "emulator did not finish booting"; exit 1 ;;
    build)
        (cd "$ROOT" && ./gradlew :app:assembleDebug -q) && ls -la "$APK" ;;
    install)
        out=$("$ADB" install -r "$APK" 2>&1); echo "$out"
        if echo "$out" | grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE; then
            echo "Installed copy is signed with another key; keeping its data across a reinstall"
            "$0" backup && "$ADB" uninstall "$PKG" && "$ADB" install "$APK" && "$0" restore
        fi
        "$ADB" shell pm grant "$PKG" android.permission.READ_SMS
        "$ADB" shell pm grant "$PKG" android.permission.RECEIVE_SMS ;;
    launch) # always a cold start on Home; a plain am start resumes whatever screen was left open
        "$ADB" logcat -c
        "$ADB" shell am start -S -n "$PKG/.MainActivity" >/dev/null && sleep 4 && echo launched ;;
    backup) # needs a debuggable build installed
        mkdir -p "$(dirname "$BACKUP")"
        "$ADB" shell am force-stop "$PKG"
        "$ADB" exec-out run-as "$PKG" tar cf - databases shared_prefs > "$BACKUP"
        (cd "$(dirname "$BACKUP")" && tar tvf "$(basename "$BACKUP")") ;;
    restore)
        "$ADB" shell am force-stop "$PKG"
        "$ADB" shell run-as "$PKG" rm -rf databases shared_prefs
        "$ADB" exec-in run-as "$PKG" tar xf - < "$BACKUP"
        "$ADB" shell run-as "$PKG" ls databases ;;
    errors) # crashes and failed writes since the last launch
        "$ADB" logcat -d | grep -E "FATAL EXCEPTION|E AndroidRuntime|E [A-Za-z]*ViewModel|Error (adding|inserting|updating|saving|choosing|deleting|moving|marking|recording|closing|reopening|linking|unlinking|restoring|excluding|reordering|changing)" \
            | grep -v uiautomator || echo "no app errors" ;;
    shot) "$ADB" exec-out screencap -p > "$SHOTS/$1.png" && echo "$SHOTS/$1.png" ;;
    dump) nodes | grep -v '^||[^|]*|false' ;;
    find) nodes | awk -F'|' -v l="$1" 'index($1 $2 $3, l)' ;;
    tap) "$ADB" shell input tap "$1" "$2" ;;
    text|id|hold)
        field=$([ "$cmd" = id ] && echo id || echo label)
        b=$(bounds_of "$1" "$field" "${2:-1}")
        [ -n "$b" ] || { echo "NOT FOUND: $1"; exit 1; }
        set -- $(center "$b")
        if [ "$cmd" = hold ]; then "$ADB" shell input swipe "$1" "$2" "$1" "$2" 900; else "$ADB" shell input tap "$1" "$2"; fi ;;
    type) "$ADB" shell input text "$1" ;;   # no spaces: everything after one is dropped
    key) "$ADB" shell input keyevent "$1" ;;
    back) "$ADB" shell input keyevent 4 ;;
    swipe) "$ADB" shell input swipe "$1" "$2" "$3" "$4" 300 ;;
    scroll) # one screenful of a vertical list
        if [ "$1" = down ]; then "$ADB" shell input swipe 700 2400 700 900 300; else "$ADB" shell input swipe 700 900 700 2400 300; fi ;;
    drawer) "$ADB" shell input tap 98 256 ;;   # the hamburger; "Open navigation drawer" by label misses
    sms) "$ADB" emu sms send "$1" "$2" ;;
    smsdel) # delete inbox SMS whose body contains the text; otherwise the app's catch-up re-imports them
        "$ADB" shell "content delete --uri content://sms --where \"body LIKE '%$1%'\""
        "$ADB" shell "content query --uri content://sms/inbox --projection _id --where \"body LIKE '%$1%'\"" ;;
    *) sed -n '2,9p' "$0"; exit 1 ;;
esac
