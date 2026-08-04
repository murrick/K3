# KANGER Server 0.14 VPS acceptance — pre-deployment inventory evidence

## Evidence source

```text
host:       v227405.hosted-by-vdsina.ru
captured:   2026-08-04T19:20:05+03:00
log name:   kanger-vps-inventory-20260804T161950Z.log
log sha256: b13e1dd25c5ca424bd0f941b7643133605d816d56bf982d61f0f48b574523047
method:     committed read-only vps-inventory.sh
```

The raw inventory log remains outside Git. This document records only sanitized operational evidence and contains no SMTP password, TLS private key, owner token or account-state content.

## Host and runtime

```text
OS:        Ubuntu 24.04.4 LTS
kernel:    6.8.0-124-generic x86_64
Java:      OpenJDK 21.0.11
root fs:   40G total / 11G used / 28G available
memory:    1.9 GiB total / approximately 0.8 GiB available
swap:      none
```

Host capacity is sufficient for the current KANGER service footprint. Absence of swap is recorded as an operational observation, not a Server 0.14 blocker.

## Existing Server baseline

```text
service:        enabled / active
installed JAR:  /opt/kanger-server/kanger-server.jar
JAR sha256:     4eb4bd33fc28a766afb24b532f743363e9ee89721360bc9e339e0848dda4a53e
branch:         server-0.13
source.branch:  server/0.13-version-identity
server.version: server-0.13
```

Loopback `/health` reports `UP`; `/ready` reports `READY`. The public health route also reports Server 0.13.

## Existing durable layout

```text
/etc/kanger-server:       root:kanger, 0750, approximately 8K
/etc/kanger-server/kanger.conf: root:kanger, 0640
/var/lib/kanger-server:   kanger:kanger, 0750, approximately 320K
/opt/kanger-server:       approximately 4.4M
```

The configuration symlink and installed JAR permissions match the deployment topology. A previous JAR is present.

## Existing registration mode

```properties
server.bind.address=127.0.0.1
server.port=1964
server.email.mode=smtps
server.url=https://api.kanger.org
server.cors.allowed.origin.1=https://kanger.org
server.cors.allowed.origin.2=https://www.kanger.org
server.cors.allow.credentials=false
```

The VPS is currently operating in the historical SMTP/SMTPS registration mode, not TRUSTED mode. The existing full configuration is preserved for backup and is not reproduced here.

## Listener and operator boundary

```text
nginx:  public 80/tcp and 443/tcp
sshd:   public 4211/tcp
KANGER: [::ffff:127.0.0.1]:1964 only
1965:   absent
```

The Server 0.13 application listener is correctly confined to IPv4 loopback. Absence of port 1965 and `/usr/local/bin/kanger-admin` is expected before Server 0.14 deployment and is the principal topology change to prove after installation.

## nginx and public routes

```text
nginx configuration: valid
ginx service:       enabled / active
https://api.kanger.org/health: Server 0.13 / UP
https://api.kanger.org/ready:  HTTP 403
https://kanger.org/:           HTTP 200
```

The public readiness boundary is already enforced. Existing API and UI virtual hosts are enabled. The default nginx site also remains enabled; this is recorded for later operational review but does not block the Server 0.14 update.

## Containers and firewall

Existing unrelated containers:

```text
amnezia-dns
amnezia-awg2 — public UDP 35039
```

They are explicitly outside this artifact and must not be modified.

Host firewall observation:

```text
ufw: absent
iptables INPUT/FORWARD/OUTPUT policies: ACCEPT
```

Server isolation therefore currently relies on loopback bindings, nginx routing and any provider-side perimeter controls. Ports 1964 and 1965 must remain unbound from public interfaces and must never be container-published.

## Additional observation

The service journal contains automated Internet probe paths such as PHP and CGI names. The current application returned HTTP 200 for those unknown-looking paths. This is not classified as a Server 0.14 deployment blocker in this artifact; it is retained as evidence for the later HTTP/UI/security audit.

## Inventory result

```text
PRE-DEPLOYMENT INVENTORY: PASS
```

The host is suitable for the controlled Server 0.13 -> Server 0.14 update. The next mandatory gate is a transactionally quiet backup of configuration and durable state, followed by off-host copy and checksum verification. No installation may begin before that gate passes.
