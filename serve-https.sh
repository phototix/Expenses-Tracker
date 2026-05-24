#!/usr/bin/env bash
set -euo pipefail

PORT="${PORT:-8080}"
HOST="${HOST:-localhost}"
CERT_DIR=".cert"
CERT_FILE="${CERT_DIR}/localhost.crt"
KEY_FILE="${CERT_DIR}/localhost.key"

if ! command -v openssl >/dev/null 2>&1; then
  echo "Error: openssl is required but not installed."
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "Error: python3 is required but not installed."
  exit 1
fi

mkdir -p "${CERT_DIR}"

if [[ ! -f "${CERT_FILE}" || ! -f "${KEY_FILE}" ]]; then
  echo "Generating self-signed certificate for ${HOST}..."
  openssl req \
    -x509 \
    -newkey rsa:2048 \
    -sha256 \
    -days 365 \
    -nodes \
    -keyout "${KEY_FILE}" \
    -out "${CERT_FILE}" \
    -subj "/CN=${HOST}" \
    -addext "subjectAltName=DNS:${HOST},IP:127.0.0.1"
fi

echo "Starting HTTPS server at https://${HOST}:${PORT}"

python3 - <<'PY'
import http.server
import ssl
import os

host = os.environ.get("HOST", "localhost")
port = int(os.environ.get("PORT", "8080"))
cert_file = os.path.join(".cert", "localhost.crt")
key_file = os.path.join(".cert", "localhost.key")

handler = http.server.SimpleHTTPRequestHandler
httpd = http.server.ThreadingHTTPServer((host, port), handler)
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(certfile=cert_file, keyfile=key_file)
httpd.socket = context.wrap_socket(httpd.socket, server_side=True)

print(f"Serving current directory over HTTPS at https://{host}:{port}")
try:
    httpd.serve_forever()
except KeyboardInterrupt:
    pass
finally:
    httpd.server_close()
PY
