#!/bin/sh
set -eu

escaped_api_base_url="$(printf '%s' "${PUBLIC_API_BASE_URL:-}" | sed 's/\\/\\\\/g; s/"/\\"/g')"

cat > /usr/share/nginx/html/config.js <<EOF
window.__PUBLIC_CONFIG__ = {
  PUBLIC_API_BASE_URL: "${escaped_api_base_url:-/api/public}"
};
EOF
