#!/bin/bash
set -e

source "$LOCAL/bin/utils"

info "Setting up the opencode AI agent…"

if ! command_exists node; then
  info "Installing Node.js LTS…"
  install_nodejs
fi

if ! command_exists opencode; then
  info "Installing opencode (this may take a moment)…"
  npm install -g --prefix /usr opencode-ai
fi

if ! command_exists yt-dlp; then
  info "Installing yt-dlp…"
  set +e
  apt install -y python3-pip >/dev/null 2>&1 && pip3 install -U yt-dlp
  ret=$?
  set -e
  if [ "$ret" -ne 0 ]; then
    warn "pip install of yt-dlp failed, trying apt package…"
    set +e
    apt install -y yt-dlp
    ret=$?
    set -e
    if [ "$ret" -ne 0 ]; then
      warn "yt-dlp installation failed; the download tool may not work"
    fi
  fi
fi

mkdir -p ~/.config/opencode/tools

if [ -f "$LOCAL/bin/agent/xed.js" ]; then
  cp "$LOCAL/bin/agent/xed.js" ~/.config/opencode/tools/xed.js
  info "Agent tools written."
else
  warn "xed.js not found in \$LOCAL/bin/agent — the app normally writes it, skipping fallback copy"
fi

if [ ! -f ~/.config/opencode/opencode.json ]; then
  info "Writing opencode config…"
  cat > ~/.config/opencode/opencode.json << 'EOF'
{
  "$schema": "https://opencode.ai/config.json",
  "instructions": ["~/.config/opencode/instructions.md"]
}
EOF
fi

if [ ! -f ~/.config/opencode/instructions.md ]; then
  warn "instructions.md missing, writing stub…"
  cat > ~/.config/opencode/instructions.md << 'EOF'
# Xed / AuraAi Agent

You are the AI agent of the Xed / AuraAi Android editor app, running inside an
Ubuntu 24.04 PRoot sandbox on the phone. The xed_* tools control the phone via
a local bridge on 127.0.0.1:9270. The sandbox has full apt/npm/pip access and
/sdcard is mounted. Downloads land in ~/Downloads and can be shared with
xed_files_to_shared.
EOF
fi

if ! grep -q "XED_BRIDGE_PORT" ~/.bashrc 2>/dev/null; then
  printf 'export XED_BRIDGE_PORT=${XED_BRIDGE_PORT:-9270}\n' >> ~/.bashrc
fi

info "Agent setup complete."
info "Authenticate once with: opencode auth login"
info "Pick the Zen / DeepSeek V4 Flash Free provider, or set a custom API key."
info "Inside opencode, press /models to pick the model."
info "Try a first prompt, e.g.: \"set volume to 10%\""
