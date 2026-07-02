#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
META="$SCRIPT_DIR/app/build/outputs/apk/debug/output-metadata.json"
DEST="/Volumes/Files/Nextcloud/WebbyPage/Documents/Projects/MyApps-Development"

SSH_HOST="46.250.228.101"
SSH_PORT="22"
SSH_USER="webbycms"
SSH_PASS="Quidents64"
REMOTE_DIR="/var/www/expenses.brandon.my"

if [ ! -f "$SRC" ]; then
    echo "Error: APK not found at $SRC"
    echo "Run './gradlew assembleDebug' first."
    exit 1
fi

VERSION=$(python3 -c "import json; d=json.load(open('$META')); print(d['elements'][0]['versionName'])")
echo "Version: $VERSION"

# --- Nextcloud ---
if [ -d "$DEST" ]; then
    echo "--- Nextcloud ---"
    find "$DEST" -maxdepth 1 -name '*ExpensesTracker*debug*' -exec rm -v {} \;
    ts=$(date +%s)
    cp "$SRC" "$DEST/${ts}_ExpensesTracker-debug.apk"
    echo "$VERSION" > "$DEST/current-version.txt"
    echo "Copied to Nextcloud"
fi

# --- Project root ---
cp "$SRC" "$SCRIPT_DIR/expensestracker-latest.apk"

# --- Contabo VPS ---
echo "--- Contabo VPS ($SSH_HOST) ---"
sshpass -p "$SSH_PASS" ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no "$SSH_USER@$SSH_HOST" "echo '$SSH_PASS' | sudo -S mkdir -p $REMOTE_DIR/apk"
sshpass -p "$SSH_PASS" scp -P "$SSH_PORT" -o StrictHostKeyChecking=no "$SRC" "$SSH_USER@$SSH_HOST:/tmp/expensestracker-latest.apk"
sshpass -p "$SSH_PASS" ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no "$SSH_USER@$SSH_HOST" "
  echo '$SSH_PASS' | sudo -S mv /tmp/expensestracker-latest.apk $REMOTE_DIR/apk/expensestracker-latest.apk
  echo '$VERSION' > /tmp/current-version.txt
  echo '$SSH_PASS' | sudo -S mv /tmp/current-version.txt $REMOTE_DIR/apk/current-version.txt
  echo '$SSH_PASS' | sudo -S chown -R www-data:www-data $REMOTE_DIR/apk
"
echo "Deployed to $REMOTE_DIR/apk/"

echo "Done"
