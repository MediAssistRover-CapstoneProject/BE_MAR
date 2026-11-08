#!/usr/bin/env bash
# Existing MAR Nginx edge. Public WSS and TURN/TLS, private plaintext upstreams.
set -Eeuo pipefail
umask 022
BACKEND= DOMAIN= TURN_DOMAIN= MEDIA_IP= EDGE_IP= MODE=
while (($#)); do
  case "$1" in
    --backend-domain) BACKEND=${2:?}; shift 2;;
    --domain) DOMAIN=${2:?}; shift 2;;
    --turn-domain) TURN_DOMAIN=${2:?}; shift 2;;
    --media-private-ip) MEDIA_IP=${2:?}; shift 2;;
    --edge-ip) EDGE_IP=${2:?}; shift 2;;
    --prepare|--apply) [[ -z $MODE ]]; MODE=$1; shift;;
    *) echo "Unknown argument: $1" >&2; exit 2;;
  esac
done

[[ $EUID == 0 ]] || { echo 'Run with sudo on the BE edge.' >&2; exit 2; }
for host in "$BACKEND" "$DOMAIN" "$TURN_DOMAIN"; do
  [[ $host =~ ^[a-z0-9][a-z0-9.-]+[a-z0-9]$ ]] || { echo 'Public DNS names required.' >&2; exit 2; }
done
[[ $BACKEND != "$DOMAIN" && $BACKEND != "$TURN_DOMAIN" && $DOMAIN != "$TURN_DOMAIN" ]]
python3 - "$EDGE_IP" "$MEDIA_IP" "$BACKEND" "$DOMAIN" "$TURN_DOMAIN" <<'PY'
import ipaddress, socket, sys
edge, media, *domains = sys.argv[1:]
networks = tuple(map(ipaddress.ip_network, ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')))
assert ipaddress.IPv4Address(edge).is_global
assert any(ipaddress.IPv4Address(media) in net for net in networks)
for host in domains:
    addresses = {a[4][0] for a in socket.getaddrinfo(host, 443, socket.AF_INET)}
    assert addresses == {edge}, 'DNS does not point to the HTTPS edge: ' + host
PY
systemctl is-active --quiet mar-backend
systemctl is-active --quiet nginx
curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health >/dev/null
nginx -t
if [[ -z $MODE ]]; then
  echo "PLAN: $DOMAIN and $TURN_DOMAIN on $EDGE_IP:443; LiveKit at $MEDIA_IP."
  exit 0
fi
exec 9>/run/lock/mar-media-edge.lock
flock -n 9

cert_dir=/etc/letsencrypt/live/mar-media
if [[ $MODE == --prepare ]]; then
  if ! dpkg-query -W -f='${db:Status-Status}' libnginx-mod-stream 2>/dev/null | grep -qx installed; then
    sources=$(mktemp /tmp/mar-apt.XXXXXX.sources)
    trap 'rm -f -- "$sources"' EXIT
    cat > "$sources" <<'APT'
Types: deb
URIs: https://ports.ubuntu.com/ubuntu-ports
Suites: noble noble-updates noble-security
Components: main universe
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
APT
    chmod 644 "$sources"
    apt=(apt-get -o "Dir::Etc::sourcelist=$sources" -o Dir::Etc::sourceparts=- -o Acquire::ForceIPv4=true)
    "${apt[@]}" update -qq
    plan=$("${apt[@]}" -s install --no-install-recommends libnginx-mod-stream)
    if grep -Eq '^Inst (nginx|nginx-common) ' <<<"$plan"; then
      echo 'NGINX_UPGRADE_REQUIRED: stopping before replacing existing nginx packages' >&2
      exit 1
    fi
    DEBIAN_FRONTEND=noninteractive "${apt[@]}" install -y --no-install-recommends libnginx-mod-stream
  fi
  install -d -m 755 /var/lib/mar-acme/.well-known/acme-challenge
  acme=/etc/nginx/conf.d/mar-media-acme.conf
  [[ ! -e $acme ]] || grep -qx '# MAR_MANAGED_MEDIA_ACME' "$acme"
  cat > "$acme" <<EOF
# MAR_MANAGED_MEDIA_ACME
server {
    listen 80;
    server_name $DOMAIN $TURN_DOMAIN;
    location ^~ /.well-known/acme-challenge/ { root /var/lib/mar-acme; }
    location / { return 404; }
}
EOF
  nginx -t
  systemctl reload nginx
  certbot certonly --webroot -w /var/lib/mar-acme --non-interactive --agree-tos --keep-until-expiring \
    --cert-name mar-media -d "$DOMAIN" -d "$TURN_DOMAIN"
  echo 'MEDIA_EDGE_CERT_READY'
  exit 0
fi

for host in "$DOMAIN" "$TURN_DOMAIN"; do
  openssl x509 -in "$cert_dir/fullchain.pem" -noout -checkhost "$host" | grep -q 'does match certificate'
done
curl -fsS --max-time 5 "http://$MEDIA_IP:7880/" >/dev/null
backend_site=/etc/nginx/sites-available/mediassistrover
nginx_conf=/etc/nginx/nginx.conf
http_config=/etc/nginx/conf.d/mar-media-wss.conf
stream_config=/etc/nginx/mar-media-stream.conf
for path in "$http_config" "$stream_config"; do
  [[ ! -e $path ]] || grep -qx '# MAR_MANAGED_MEDIA_EDGE' "$path"
done
[[ -f $backend_site && -L /etc/nginx/sites-enabled/mediassistrover ]]
work=$(mktemp -d /tmp/mar-media-edge.XXXXXX)
files=("$backend_site" "$nginx_conf" "$http_config" "$stream_config")
for path in "${files[@]}"; do
  if [[ -e $path ]]; then cp -p "$path" "$work/${path##*/}"; fi
done
rollback() {
  local status=$?
  if ((status != 0)); then
    for path in "${files[@]}"; do
      if [[ -f $work/${path##*/} ]]; then
        cp -p "$work/${path##*/}" "$path"
      else
        rm -f -- "$path"
      fi
    done
    nginx -t && systemctl reload nginx
    echo 'MEDIA_EDGE_CONFIG_RESTORED' >&2
  fi
  rm -rf -- "$work"
  exit "$status"
}
trap rollback EXIT

python3 - "$backend_site" <<'PY'
import pathlib, re, sys
path = pathlib.Path(sys.argv[1])
source = path.read_text()
if '# MAR_MEDIA_EDGE_BE' not in source:
    old = r'(?m)^([ \t]*)listen 443 ssl;[^\n]*$'
    replacement = (
        r'\1listen 127.0.0.1:4443 ssl proxy_protocol default_server; # MAR_MEDIA_EDGE_BE'
        r'\n\1set_real_ip_from 127.0.0.1;'
        r'\n\1real_ip_header proxy_protocol;'
    )
    result, count = re.subn(old, replacement, source)
    if count != 1:
        raise RuntimeError('Expected exactly one existing backend HTTPS listener')
    path.write_text(result)
PY
cat > "$http_config" <<EOF
# MAR_MANAGED_MEDIA_EDGE
map \$http_upgrade \$mar_media_connection {
    default upgrade;
    '' close;
}
server {
    listen 127.0.0.1:4443 ssl proxy_protocol;
    server_name $DOMAIN;
    set_real_ip_from 127.0.0.1;
    real_ip_header proxy_protocol;
    ssl_certificate $cert_dir/fullchain.pem;
    ssl_certificate_key $cert_dir/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    access_log off;
    error_log /var/log/nginx/mar-media-wss.error.log crit;
    location = / { return 200 'MAR Media'; }
    location ~ ^/rtc(?:/|$) {
        proxy_pass http://$MEDIA_IP:7880;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \$mar_media_connection;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$remote_addr;
        proxy_set_header X-Forwarded-Proto https;
        proxy_buffering off;
        proxy_connect_timeout 3s;
        proxy_read_timeout 3600s;
    }
    location / { return 404; }
}
EOF
cat > "$stream_config" <<EOF
# MAR_MANAGED_MEDIA_EDGE
stream {
    map \$ssl_preread_server_name \$mar_media_sni_target {
        $TURN_DOMAIN 127.0.0.1:5443;
        default 127.0.0.1:4443;
    }
    server {
        listen 443;
        ssl_preread on;
        proxy_pass \$mar_media_sni_target;
        proxy_protocol on;
        proxy_connect_timeout 3s;
        proxy_timeout 3600s;
    }
    server {
        listen 127.0.0.1:5443 ssl proxy_protocol;
        set_real_ip_from 127.0.0.1;
        ssl_certificate $cert_dir/fullchain.pem;
        ssl_certificate_key $cert_dir/privkey.pem;
        ssl_protocols TLSv1.2 TLSv1.3;
        proxy_pass $MEDIA_IP:443;
        proxy_protocol on;
        proxy_connect_timeout 3s;
        proxy_timeout 3600s;
    }
}
EOF
if ! grep -qxF "include $stream_config;" "$nginx_conf"; then
  printf '\ninclude %s;\n' "$stream_config" >> "$nginx_conf"
fi
nginx -t
systemctl reload nginx
for attempt in $(seq 1 15); do
  if curl -fsS --max-time 2 --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/" 2>/dev/null | grep -qx 'MAR Media'; then
    break
  fi
  sleep 1
done
curl -fsS --max-time 4 --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/" | grep -qx 'MAR Media'
curl -fsS --max-time 4 --resolve "$BACKEND:443:127.0.0.1" "https://$BACKEND/actuator/health" | grep -q '"status":"UP"'
timeout 6 openssl s_client -connect 127.0.0.1:443 -servername "$TURN_DOMAIN" -verify_hostname "$TURN_DOMAIN" -verify_return_error -brief </dev/null
install -d -m 755 /etc/letsencrypt/renewal-hooks/deploy
cat > /etc/letsencrypt/renewal-hooks/deploy/mar-media-edge <<'HOOK'
#!/bin/sh
set -eu
[ "${RENEWED_LINEAGE:-}" = /etc/letsencrypt/live/mar-media ] || exit 0
nginx -t
systemctl reload nginx
HOOK
chmod 750 /etc/letsencrypt/renewal-hooks/deploy/mar-media-edge
echo "MEDIA_EDGE_READY wss://$DOMAIN turns:$TURN_DOMAIN:443"
