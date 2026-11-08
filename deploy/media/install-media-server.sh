#!/usr/bin/env bash
# Dedicated Ubuntu ARM64 media host. TLS terminates at the existing HTTPS edge.
set -Eeuo pipefail
umask 077
VERSION=1.13.11
ARTIFACT_SHA=2af6ce091d6b3c79d1677f8ff1bbb91a8007f40b2325c059b52d38383c0830b6
DOMAIN= TURN_DOMAIN= PUBLIC_IP= PRIVATE_IP= EDGE_IP= EDGE_PRIVATE_IP= APPLY=0
while (($#)); do
  case "$1" in
    --domain) DOMAIN=${2:?}; shift 2;;
    --turn-domain) TURN_DOMAIN=${2:?}; shift 2;;
    --public-ip) PUBLIC_IP=${2:?}; shift 2;;
    --private-ip) PRIVATE_IP=${2:?}; shift 2;;
    --edge-ip) EDGE_IP=${2:?}; shift 2;;
    --edge-private-ip) EDGE_PRIVATE_IP=${2:?}; shift 2;;
    --apply) APPLY=1; shift;;
    *) echo "Unknown argument: $1" >&2; exit 2;;
  esac
done

[[ $EUID == 0 && $(uname -m) == aarch64 ]] || { echo 'Run with sudo on ARM64 Ubuntu.' >&2; exit 2; }
if systemctl is-active --quiet mar-backend.service; then
  echo 'DEDICATED_MEDIA_INSTANCE_REQUIRED' >&2; exit 2
fi
[[ $DOMAIN =~ ^[a-z0-9][a-z0-9.-]+[a-z0-9]$ && $TURN_DOMAIN =~ ^[a-z0-9][a-z0-9.-]+[a-z0-9]$ && $DOMAIN != "$TURN_DOMAIN" ]] || { echo 'Two distinct public DNS names required.' >&2; exit 2; }
python3 - "$DOMAIN" "$TURN_DOMAIN" "$PUBLIC_IP" "$PRIVATE_IP" "$EDGE_IP" "$EDGE_PRIVATE_IP" <<'PY'
import ipaddress, json, socket, subprocess, sys
domain, turn, public, private, edge, edge_private = sys.argv[1:]
networks = tuple(map(ipaddress.ip_network, ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')))
for value in (private, edge_private):
    assert any(ipaddress.IPv4Address(value) in net for net in networks), 'Private VPC IPv4 required'
for value in (public, edge):
    assert ipaddress.IPv4Address(value).is_global, 'Public IPv4 required'
assert private != edge_private and public != edge, 'Use separate media and ingress hosts'
interfaces = json.loads(subprocess.check_output(['ip', '-j', '-4', 'address']))
assert any(addr['local'] == private for interface in interfaces for addr in interface['addr_info']), 'Media IP is not assigned here'
for host in (domain, turn):
    addresses = {a[4][0] for a in socket.getaddrinfo(host, 443, socket.AF_INET)}
    assert addresses == {edge}, 'DNS does not point to the HTTPS edge: ' + host
print('MEDIA_PREFLIGHT_OK')
PY
if ((! APPLY)); then
  echo "PLAN: LiveKit $VERSION on $PRIVATE_IP:7880, WebRTC $PUBLIC_IP:7881/TCP and :7882/UDP, TURN via $TURN_DOMAIN."
  exit 0
fi

work=$(mktemp -d /tmp/mar-media-install.XXXXXX)
trap 'rm -rf -- "$work"' EXIT
artifact="livekit_${VERSION}_linux_arm64.tar.gz"
curl -fLSs --retry 3 "https://github.com/livekit/livekit/releases/download/v${VERSION}/$artifact" -o "$work/$artifact"
(cd "$work"; printf '%s  %s\n' "$ARTIFACT_SHA" "$artifact" | sha256sum --check --status)
tar -xzf "$work/$artifact" -C "$work" livekit-server
install -m 755 "$work/livekit-server" /usr/local/bin/mar-livekit
getent passwd marmedia >/dev/null || useradd --system --home-dir /var/lib/mar-media --shell /usr/sbin/nologin marmedia
install -d -m 750 -o root -g marmedia /etc/mediassistrover
install -d -m 750 -o marmedia -g marmedia /var/lib/mar-media

python3 - "$DOMAIN" "$TURN_DOMAIN" "$PRIVATE_IP" "$EDGE_PRIVATE_IP" <<'PY'
import grp, json, os, pathlib, secrets, sys, tempfile

domain, turn, private, edge_private = sys.argv[1:]
root = pathlib.Path('/etc/mediassistrover')
credentials = root / 'media-credentials.json'
if not credentials.exists():
    fd = os.open(credentials, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as handle:
        json.dump({'api_key': 'MAR_' + secrets.token_hex(12), 'api_secret': secrets.token_hex(32)}, handle)
key = json.loads(credentials.read_text())
group = grp.getgrnam('marmedia').gr_gid

def save(name, content, mode, gid=0):
    with tempfile.NamedTemporaryFile(mode='w', dir=root, delete=False) as handle:
        path = pathlib.Path(handle.name)
        try:
            handle.write(content)
            handle.flush()
            os.fchown(handle.fileno(), 0, gid)
            os.fchmod(handle.fileno(), mode)
            path.replace(root / name)
        finally:
            path.unlink(missing_ok=True)

save('livekit.yaml', f'''# MAR_MANAGED_MEDIA_V1
port: 7880
bind_addresses: ["127.0.0.1", "{private}"]
rtc:
  tcp_port: 7881
  udp_port: 7882
  use_external_ip: true
room:
  auto_create: false
  empty_timeout: 60
  departure_timeout: 15
  max_participants: 2
keys:
  {key['api_key']}: {key['api_secret']}
turn:
  enabled: true
  domain: {turn}
  tls_port: 443
  udp_port: 3478
  bind_addresses: ["{private}"]
  external_tls: true
  proxy_protocol: true
  proxy_protocol_trusted_cidrs: ["{edge_private}/32"]
logging:
  level: warn
''', 0o640, group)
save('media-backend.env', f'''MEDIA_ENABLED=true
MEDIA_URL=wss://{domain}
MEDIA_INTERNAL_URL=http://{private}:7880
MEDIA_API_KEY={key['api_key']}
MEDIA_API_SECRET={key['api_secret']}
MEDIA_TOKEN_TTL_SEC=60
MEDIA_SESSION_MAX_SEC=1800
MEDIA_RING_TIMEOUT_SEC=60
MEDIA_DEVICE_TIMEOUT_SEC=35
''', 0o600)
PY

cat > /etc/systemd/system/mar-media.service <<'EOF'
[Unit]
Description=MAR WebRTC SFU and TURN
After=network-online.target
Wants=network-online.target
StartLimitIntervalSec=120
StartLimitBurst=5

[Service]
User=marmedia
Group=marmedia
ExecStart=/usr/local/bin/mar-livekit --config /etc/mediassistrover/livekit.yaml
Restart=on-failure
RestartSec=5
MemoryHigh=512M
MemoryMax=768M
CPUQuota=150%
TasksMax=256
LimitNOFILE=65536
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX AF_NETLINK

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable mar-media.service
systemctl reset-failed mar-media.service
systemctl restart mar-media.service
for attempt in $(seq 1 20); do
  if curl -fsS --max-time 2 http://127.0.0.1:7880/ >/dev/null 2>&1; then
    echo "MEDIA_CONTROL_READY http://$PRIVATE_IP:7880"
    echo 'Root-only backend settings: /etc/mediassistrover/media-backend.env'
    exit 0
  fi
  sleep 1
done
echo 'MEDIA_STARTUP_FAILED: inspect journalctl -u mar-media.service' >&2
exit 1
