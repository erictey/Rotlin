#!/bin/bash
# Rotlin one-click install for macOS. Double-click me.
DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v java >/dev/null 2>&1; then
  echo ""
  echo "[X] Java not found."
  echo "    Install a JDK 21+ first:  brew install temurin@21"
  echo "    (or https://adoptium.net/temurin/releases/?version=21 )"
  echo "    Then run this again."
  echo ""
  read -p "Press enter to close..."
  exit 1
fi

chmod +x "$DIR/bin/rotlin"

# zsh is the default macOS shell
PROFILE="$HOME/.zshrc"
LINE="export PATH=\"$DIR/bin:\$PATH\""
if grep -qF "$DIR/bin" "$PROFILE" 2>/dev/null; then
  echo "[OK] Rotlin already on your PATH."
else
  echo "$LINE" >> "$PROFILE"
  echo "[OK] Added Rotlin to your PATH."
fi

echo ""
echo "Done. Open a NEW terminal, then run:"
echo ""
echo "    rotlin cook examples/hello.rot"
echo ""
read -p "Press enter to close..."
