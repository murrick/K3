#!/usr/bin/env bash
set -uo pipefail
export LC_ALL=C

section() {
  printf '\n===== %s =====\n' "$1"
}

run() {
  printf '\n$'
  printf ' %q' "$@"
  printf '\n'
  "$@" 2>&1 || printf '[exit=%s]\n' "$?"
}

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=()
elif command -v sudo >/dev/null 2>&1; then
  SUDO=(sudo)
else
  SUDO=()
fi

section "identity and clock"
run date --iso-8601=seconds
run hostname -f
run id
run uptime

section "operating system and capacity"
run uname -a
if [[ -r /etc/os-release ]]; then
  run cat /etc/os-release
fi
run df -hT /
run free -h

section "runtime prerequisites"
for command in java curl systemctl nginx ss runuser sha256sum; do
  if command -v "${command}" >/dev/null 2>&1; then
    printf '%-12s %s\n' "${command}" "$(command -v "${command}")"
  else
    printf '%-12s MISSING\n' "${command}"
  fi
done
run java -version

section "KANGER service state"
run "${SUDO[@]}" systemctl is-enabled kanger-server.service
run "${SUDO[@]}" systemctl is-active kanger-server.service
run "${SUDO[@]}" systemctl status kanger-server.service --no-pager --full -n 30

section "installed files and permissions"
for path in \
  /opt/kanger-server/kanger-server.jar \
  /opt/kanger-server/kanger-server.jar.previous \
  /etc/kanger-server \
  /etc/kanger-server/kanger.conf \
  /var/lib/kanger-server \
  /var/lib/kanger-server/kanger.conf \
  /usr/local/bin/kanger-admin \
  /etc/systemd/system/kanger-server.service; do
  if "${SUDO[@]}" test -e "${path}" || "${SUDO[@]}" test -L "${path}"; then
    "${SUDO[@]}" stat -c '%A %U:%G %s %n -> %N' "${path}" 2>&1 || true
  else
    printf 'MISSING %s\n' "${path}"
  fi
done

if "${SUDO[@]}" test -f /opt/kanger-server/kanger-server.jar; then
  run "${SUDO[@]}" sha256sum /opt/kanger-server/kanger-server.jar
  if command -v unzip >/dev/null 2>&1; then
    run "${SUDO[@]}" unzip -p /opt/kanger-server/kanger-server.jar org/kanger/build.properties
  fi
fi
run "${SUDO[@]}" du -sh /etc/kanger-server /var/lib/kanger-server /opt/kanger-server

section "safe KANGER configuration keys"
if "${SUDO[@]}" test -r /etc/kanger-server/kanger.conf; then
  "${SUDO[@]}" awk -F= '
    /^[[:space:]]*#/ { next }
    /^(server\.bind\.address|server\.port|server\.admin\.enabled|server\.admin\.bind\.address|server\.admin\.port|server\.email\.mode|server\.url|server\.confirmation\.redirect\.url|server\.cors\.allowed\.origin\.[0-9]+|server\.cors\.allow\.credentials)=/ {
      print
    }
  ' /etc/kanger-server/kanger.conf
else
  echo 'Configuration is absent or unreadable.'
fi

section "loopback health and readiness"
run curl --fail --silent --show-error --max-time 5 http://127.0.0.1:1964/health
printf '\n'
run curl --fail --silent --show-error --max-time 5 http://127.0.0.1:1964/ready
printf '\n'

section "listeners"
if [[ "${#SUDO[@]}" -gt 0 ]]; then
  "${SUDO[@]}" ss -ltnp 2>&1 | grep -E ':(80|443|1964|1965|4211)\b' || true
else
  ss -ltnp 2>&1 | grep -E ':(80|443|1964|1965|4211)\b' || true
fi

section "nginx"
run "${SUDO[@]}" nginx -t
run "${SUDO[@]}" systemctl is-enabled nginx
run "${SUDO[@]}" systemctl is-active nginx
if "${SUDO[@]}" test -d /etc/nginx/sites-enabled; then
  run "${SUDO[@]}" ls -la /etc/nginx/sites-enabled
  "${SUDO[@]}" grep -RHE '^[[:space:]]*(listen|server_name|proxy_pass)[[:space:]]' \
    /etc/nginx/sites-enabled 2>/dev/null || true
fi

section "containers and host firewall"
if command -v docker >/dev/null 2>&1; then
  run "${SUDO[@]}" docker ps --format 'table {{.ID}}\t{{.Names}}\t{{.Ports}}'
else
  echo 'docker not installed'
fi
if command -v ufw >/dev/null 2>&1; then
  run "${SUDO[@]}" ufw status verbose
else
  echo 'ufw not installed'
fi
if command -v iptables >/dev/null 2>&1; then
  "${SUDO[@]}" iptables -S 2>/dev/null \
    | grep -E '(^-P|--dport (80|443|1964|1965|4211))' || true
  "${SUDO[@]}" iptables -t nat -S 2>/dev/null \
    | grep -E -- '--dport (80|443|1964|1965|4211)' || true
fi

section "public routes"
run curl --silent --show-error --max-time 10 https://api.kanger.org/health
printf '\n'
run curl --silent --show-error --output /dev/null --max-time 10 \
  --write-out 'api /ready HTTP %{http_code}\n' https://api.kanger.org/ready
run curl --silent --show-error --output /dev/null --max-time 10 \
  --write-out 'UI HTTP %{http_code}\n' https://kanger.org/

section "inventory complete"
echo 'Read-only inventory finished. No configuration, service, firewall or durable state was changed.'
