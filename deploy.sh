#!/bin/bash
set -e
echo "==> Building image..."
podman build -t watchshark:latest -f Containerfile .
echo "==> Starting container (compose)..."
podman-compose -f podman-compose.yml up -d
echo "==> Waiting 5s for boot..."
sleep 5
podman logs --tail 20 watchshark || true
echo ""
echo "==> Local test: http://localhost:3001/health"
curl -s http://localhost:3001/health || echo "(not up yet, check logs)"
echo ""
echo "NEXT STEPS:"
echo "1. In duckdns.org dashboard create subdomain 'watchshark' (same IP as matkoimmich)."
echo "   If your duckdns container uses SUBDOMAINS env, add watchshark and restart it."
echo "2. Append caddy-snippet.txt to ~/containers/caddy/Caddyfile"
echo "3. podman exec caddy caddy reload --config /etc/caddy/Caddyfile"
echo "4. Open https://watchshark.duckdns.org"
