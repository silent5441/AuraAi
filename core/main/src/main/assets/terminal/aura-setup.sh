#!/bin/bash
# AuraAi one-click environment setups.
# Usage: aura-setup <recipe>
# Recipes: agent | nodejs | opencode | ytdlp | python | cpp | java | go | rust | php

set -e

source "$LOCAL/bin/utils"

NODE_MAJOR=20

ensure_apt_pkgs() {
  local missing=()
  local pkg
  for pkg in "$@"; do
    if ! dpkg -s "$pkg" >/dev/null 2>&1; then
      missing+=("$pkg")
    fi
  done

  if [ ${#missing[@]} -eq 0 ]; then
    return 0
  fi

  info "Installing packages: ${missing[*]}"
  if ! DEBIAN_FRONTEND=noninteractive apt-get install -y "${missing[@]}"; then
    info "Refreshing package index…"
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y "${missing[@]}"
  fi
}

setup_nodejs() {
  if command_exists node; then
    info "Node.js already installed: $(node -v)"
    return 0
  fi

  info "Installing prerequisites (ca-certificates, curl, gnupg)…"
  ensure_apt_pkgs ca-certificates curl gnupg

  info "Adding NodeSource repository (Node.js ${NODE_MAJOR}.x)…"
  install -d -m 0755 /etc/apt/keyrings
  curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key |
    gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg

  echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main" \
    >/etc/apt/sources.list.d/nodesource.list

  info "Updating package index…"
  apt-get update -qq

  info "Installing Node.js ${NODE_MAJOR}…"
  apt-get install -y nodejs

  info "Node.js $(node -v) and npm v$(npm -v) installed."
}

setup_opencode() {
  if ! command_exists node; then
    setup_nodejs
  fi

  if command_exists opencode; then
    info "opencode already installed: $(opencode --version 2>/dev/null | head -n1)"
  else
    info "Installing opencode CLI via npm (this may take a moment)…"
    set +e
    npm install -g --prefix /usr opencode-ai
    ret=$?
    set -e
    if [ "$ret" -ne 0 ]; then
      warn "npm install failed, falling back to the official installer…"
      curl -fsSL https://opencode.ai/install | bash
      if ! grep -q ".opencode/bin" ~/.bashrc 2>/dev/null; then
        printf 'export PATH="$HOME/.opencode/bin:$PATH"\n' >>~/.bashrc
      fi
      export PATH="$HOME/.opencode/bin:$PATH"
    fi
  fi

  # Make sure globally installed npm binaries are reachable in new shells.
  if ! grep -q "npm-global" ~/.bashrc 2>/dev/null; then
    printf '# npm global binaries\nexport PATH="$PATH:$(npm bin -g 2>/dev/null)"\n' >>~/.bashrc || true
  fi

  if command_exists opencode; then
    info "opencode $(opencode --version 2>/dev/null | head -n1) installed."
    info "Run 'opencode auth login' once to pick a free provider."
  else
    error "opencode installation failed."
    exit 1
  fi
}

setup_ytdlp() {
  ensure_apt_pkgs ffmpeg
  if command_exists yt-dlp; then
    info "yt-dlp already installed: $(yt-dlp --version 2>/dev/null)"
    return 0
  fi
  info "Installing yt-dlp…"
  ensure_apt_pkgs python3-pip
  set +e
  pip3 install -U --break-system-packages yt-dlp ||
    pip3 install -U yt-dlp
  ret=$?
  set -e
  if [ "$ret" -ne 0 ]; then
    warn "pip install failed, trying the apt package…"
    apt-get remove -y yt-dlp >/dev/null 2>&1 || true
    ensure_apt_pkgs yt-dlp
  fi
  info "yt-dlp $(yt-dlp --version 2>/dev/null) installed."
}

setup_agent() {
  info "Setting up the full AI agent stack…"
  setup_opencode
  setup_ytdlp
  if [ -x "$LOCAL/bin/agent-setup" ]; then
    "$LOCAL/bin/agent-setup"
  else
    mkdir -p ~/.config/opencode/tools
    if [ -f "$LOCAL/bin/agent/xed.js" ] || [ -f ~/.config/opencode/tools/xed.js ]; then
      [ -f "$LOCAL/bin/agent/xed.js" ] && cp -f "$LOCAL/bin/agent/xed.js" ~/.config/opencode/tools/xed.js
      info "Agent tools written."
    fi
  fi
  info "AI agent stack ready. Open opencode and start prompting!"
}

setup_python() {
  if command_exists python3 && command_exists pip3; then
    info "Python already installed: $(python3 --version)"
    return 0
  fi
  info "Installing Python 3, pip and venv…"
  ensure_apt_pkgs python3 python3-pip python3-venv
  info "$(python3 --version) with pip $(pip3 --version | awk '{print $2}') installed."
}

setup_cpp() {
  if command_exists gcc && command_exists make && command_exists g++; then
    info "C/C++ toolchain already installed: $(gcc --version | head -n1)"
    return 0
  fi
  info "Installing C/C++ toolchain (gcc, g++, make, gdb, cmake)…"
  ensure_apt_pkgs build-essential gdb cmake
  info "C/C++ toolchain installed."
}

setup_java() {
  if command_exists javac; then
    info "JDK already installed: $(javac --version)"
    return 0
  fi
  info "Installing OpenJDK 21 (headless JDK)…"
  ensure_apt_pkgs openjdk-21-jdk-headless
  info "$(javac --version) installed."
}

setup_go() {
  if command_exists go; then
    info "Go already installed: $(go version)"
    return 0
  fi
  info "Installing Go…"
  ensure_apt_pkgs golang-go
  info "$(go version) installed."
}

setup_rust() {
  if command_exists rustc && command_exists cargo; then
    info "Rust already installed: $(rustc --version)"
    return 0
  fi
  info "Installing Rust (rustc + cargo)…"
  ensure_apt_pkgs rustc cargo
  info "$(rustc --version) installed."
}

setup_php() {
  if command_exists php; then
    info "PHP already installed: $(php --version | head -n1)"
    return 0
  fi
  info "Installing PHP CLI…"
  ensure_apt_pkgs php-cli php-mbstring php-xml
  info "$(php --version | head -n1) installed."
}

usage() {
  cat <<'EOF'
AuraAi one-click setups

Usage: aura-setup <recipe>

Recipes:
  agent     Full AI stack (Node.js + opencode + yt-dlp + xed tools)
  nodejs    Node.js 20 LTS + npm (NodeSource)
  opencode  opencode AI coding CLI
  ytdlp     yt-dlp + ffmpeg
  python    Python 3 + pip + venv
  cpp       gcc / g++ / make / gdb / cmake
  java      OpenJDK 21 (JDK)
  go        Go toolchain
  rust      rustc + cargo
  php       PHP CLI
EOF
}

RECIPE="${1:-}"

if [ -z "$RECIPE" ]; then
  usage
  exit 0
fi

case "$RECIPE" in
  help | -h | --help)
    usage
    exit 0
    ;;
esac

trap 'error "Setup \"$RECIPE\" failed. Scroll up for details."' EXIT

case "$RECIPE" in
  agent) setup_agent ;;
  nodejs) setup_nodejs ;;
  opencode) setup_opencode ;;
  ytdlp) setup_ytdlp ;;
  python) setup_python ;;
  cpp) setup_cpp ;;
  java) setup_java ;;
  go) setup_go ;;
  rust) setup_rust ;;
  php) setup_php ;;
  *)
    error "Unknown recipe: $RECIPE"
    usage
    exit 1
    ;;
esac

trap - EXIT
info "✔ '$RECIPE' setup complete."
