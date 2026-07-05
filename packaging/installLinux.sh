#!/bin/bash
# Rotlin one-click install for Linux. Run:  bash installLinux.sh
DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v java >/dev/null 2>&1; then
  echo ""
  echo "[X] Java not found."
  echo "    Install a JDK 21+ first:  sudo apt install openjdk-21-jdk"
  echo "    Then run this again."
  echo ""
  read -p "Press enter to close..."
  exit 1
fi

chmod +x "$DIR/bin/rotlin"

PROFILE="$HOME/.bashrc"
LINE="export PATH=\"$DIR/bin:\$PATH\""
if grep -qF "$DIR/bin" "$PROFILE" 2>/dev/null; then
  echo "[OK] Rotlin already on your PATH."
else
  echo "$LINE" >> "$PROFILE"
  echo "[OK] Added Rotlin to your PATH."
fi

echo ""
echo "Done. Open a NEW terminal (or run: source ~/.bashrc), then run:"
echo ""
echo "    rotlin cook examples/hello.rot"
echo ""
read -p "Press enter to close..."
