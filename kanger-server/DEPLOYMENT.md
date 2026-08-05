# KANGER Server 0.16 VPS deployment

This guide deploys the qualified standalone KANGER Server 0.16 JAR on a
Debian/Ubuntu-style systemd host behind nginx.

## Release topology

```text
Internet
   |
   +--> https://kanger.org
   |      nginx static UI
   |      repository html/
   |
   +--> https://api.kanger.org
          nginx reverse proxy
             |
             v
        127.0.0.1:1964
        public application API

host operator
   |
   +--> sudo kanger-admin
             |
             v
        127.0.0.1:1965
        owner-only operator API
```

The Java listeners on ports `1964` and `1965` must remain loopback-only.
nginx proxies only port `1964`. Port `1965` and the owner bearer token are never
exposed through nginx, the public API or the browser UI.

The qualified release identity is:

```json
{
  "version": "3.3",
  "core_version": "3.3",
  "api_version": "1",
  "server_version": "server-0.16"
}
```

`version` and `core_version` identify semantic KANGER compatibility.
`server_version` identifies this deployable server artifact.

## 1. Build the immutable release artifact

Use the qualified three-digit shelf after it has been created:

```bash
git fetch origin
git switch develop/server/0.16
git status --short

mvn -B -ntp \
  -f kanger-server/pom.xml \
  -Dkanger.build.branch.override=develop/server/0.16 \
  clean verify
```

Do not deploy from a working tree with unexplained local changes. The generated
`org/kanger/build.properties` must contain:

```properties
branch=server-0.16
server.version=server-0.16
source.branch=develop/server/0.16
```

The deployable file is:

```text
kanger-server/target/kanger-server.jar
```

Run the isolated local process:

```bash
bash kanger-server/scripts/run-local.sh
```

In another terminal:

```bash
bash kanger-server/scripts/smoke-local.sh
bash kanger-server/scripts/smoke-auth-local.sh
```

Both scripts must complete successfully before copying the distribution.

## 2. Copy the distribution to the VPS

Set the real SSH destination and port:

```bash
SSH_TARGET=user@vps
SSH_PORT=22
```

Prepare a clean temporary directory:

```bash
ssh -p "${SSH_PORT}" "${SSH_TARGET}" \
  'rm -rf /tmp/kanger-deploy && mkdir -m 700 /tmp/kanger-deploy'
```

Copy the JAR and deployment assets:

```bash
scp -P "${SSH_PORT}" \
  kanger-server/target/kanger-server.jar \
  "${SSH_TARGET}:/tmp/kanger-server.jar"

scp -P "${SSH_PORT}" -r \
  kanger-server/deploy/. \
  "${SSH_TARGET}:/tmp/kanger-deploy/"
```

The deployment directory includes:

```text
install.sh
verify-installed.sh
kanger-admin
kanger.conf.example
systemd/kanger-server.service
nginx/kanger-server.conf.template
```

## 3. Verify host prerequisites

KANGER Server is qualified on Java 8 and Java 21. Java 21 is recommended for
the installed service.

```bash
java -version
readlink -f "$(command -v java)"
test -x /usr/bin/java
command -v curl systemctl nginx ss runuser
```

On a compatible Debian/Ubuntu host, install missing runtime tools with:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless curl nginx iproute2
```

Do not continue until `/usr/bin/java`, `curl`, `systemctl`, `nginx`, `ss` and
`runuser` are available.

## 4. Install or update the service

Run the installer:

```bash
sudo bash /tmp/kanger-deploy/install.sh \
  /tmp/kanger-server.jar
```

The installer:

1. creates the system user and group `kanger` when absent;
2. creates `/opt/kanger-server`, `/var/lib/kanger-server`, and
   `/etc/kanger-server`;
3. installs the JAR as `/opt/kanger-server/kanger-server.jar`;
4. preserves the previous JAR as `kanger-server.jar.previous`;
5. installs `/usr/local/bin/kanger-admin`;
6. installs and enables `kanger-server.service`;
7. creates `/etc/kanger-server/kanger.conf` only on first installation;
8. links that configuration into the service `user.home`;
9. waits for application health and readiness;
10. restores the previous JAR automatically if startup qualification fails.

The state root is:

```text
/var/lib/kanger-server/KANGER
```

The owner-only admin token is generated under that root and is consumed by
`sudo kanger-admin`; it must not be copied into shell history, browser
configuration or nginx files.

## 5. Configure the release topology

The persistent configuration is:

```text
/etc/kanger-server/kanger.conf
```

The minimum private-listener topology is:

```properties
server.bind.address=127.0.0.1
server.port=1964

server.admin.enabled=true
server.admin.bind.address=127.0.0.1
server.admin.port=1965
server.admin.token.file=KANGER/admin.token
```

Never change either bind address to `0.0.0.0`, `[::]`, a public VPS address or a
container bridge address.

The public API and browser redirect boundaries are:

```properties
server.url=https://api.kanger.org
server.confirmation.redirect.url=https://kanger.org/
```

Allow only the exact browser origins that call the API:

```properties
server.cors.allowed.origin.1=https://kanger.org
server.cors.allowed.origin.2=https://www.kanger.org
server.cors.allow.credentials=false
```

When `www.kanger.org` redirects before the UI loads, only the canonical origin
is required. Never use a wildcard origin with credentials.

After configuration changes:

```bash
sudo systemctl restart kanger-server.service
sudo systemctl status kanger-server.service --no-pager
```

## 6. Choose the registration policy

Server 0.16 resolves `server.email.mode` once into the account registration
policy.

### TRUSTED deployment

```properties
server.email.mode=disabled
```

Result:

```text
public self-registration disabled
Register absent from the browser gateway
complete ACTIVE accounts created only through kanger-admin
ordinary login creates a session
```

There is no password-only public-registration fallback in TRUSTED mode.

Create an account interactively:

```bash
sudo kanger-admin create-user
```

Use explicit standard input only for controlled automation:

```bash
printf '%s\n' "${NEW_PASSWORD}" \
  | sudo kanger-admin create-user \
      --login new-user \
      --email new-user@example.org \
      --password-stdin
```

Delete an account only after reviewing the target and confirming the destructive
operation:

```bash
sudo kanger-admin delete-user --login new-user
```

The complete operator command, exit-code and recovery contract is documented in:

```text
kanger-server/docs/operations/kanger-admin.md
```

### EMAIL_VERIFIED deployment

Choose exactly one mail transport:

```properties
server.email.mode=starttls
```

or:

```properties
server.email.mode=smtps
```

Result:

```text
public registration creates PendingRegistration only
e-mail confirmation creates the complete ACTIVE account
confirmation does not create a session
ordinary login creates the session
```

Configure SMTP credentials and timeouts according to:

```text
kanger-server/MAIL-CONFIGURATION.md
```

Protect the configuration after adding credentials:

```bash
sudo chown root:kanger /etc/kanger-server/kanger.conf
sudo chmod 0640 /etc/kanger-server/kanger.conf
sudo systemctl restart kanger-server.service
```

## 7. Verify the installed service

Run the permanent installation verifier:

```bash
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

It proves:

```text
systemd service enabled and active
/health reports server-0.16
/ready reports server-0.16
application listener 127.0.0.1:1964
operator listener 127.0.0.1:1965
neither Java listener publicly bound
nginx configuration valid
```

Manual checks:

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:1964/health
echo

curl --fail --silent --show-error \
  http://127.0.0.1:1964/ready
echo

sudo ss -H -ltn \
  '( sport = :1964 or sport = :1965 )'
```

Expected health identity:

```json
{
  "result": "OK",
  "status": "UP",
  "version": "3.3",
  "core_version": "3.3",
  "api_version": "1",
  "server_version": "server-0.16"
}
```

Linux may display an IPv4 loopback listener as
`[::ffff:127.0.0.1]:<port>`. It must not display `0.0.0.0:<port>` or
`[::]:<port>`.

## 8. Configure nginx and public HTTPS

Before enabling nginx, confirm that no container or other process owns public
ports `80` or `443`:

```bash
sudo ss -ltnp | grep -E ':(80|443)\b' || true
sudo docker ps --format \
  'table {{.ID}}\t{{.Names}}\t{{.Ports}}' 2>/dev/null || true
sudo iptables -t nat -S 2>/dev/null \
  | grep -- '--dport 443' || true
```

Do not remove Docker-generated NAT rules manually. Stop, remove or remap the
container that owns the conflicting publication.

Render the supplied API configuration:

```bash
sudo sed 's/KANGER_DOMAIN/api.kanger.org/g' \
  /tmp/kanger-deploy/nginx/kanger-server.conf.template \
  | sudo tee /etc/nginx/sites-available/kanger-server.conf >/dev/null
```

The template proxies the public API to `127.0.0.1:1964`, exposes `/health`,
keeps detailed `/ready` metrics local, forwards `X-Request-ID`, and contains no
route to port `1965`.

Use one TLS model:

- a Let's Encrypt certificate for `api.kanger.org`; or
- a Cloudflare Origin CA certificate covering `kanger.org` and `*.kanger.org`
  with Cloudflare SSL/TLS mode `Full (strict)`.

Install private keys with mode `0600`, validate the certificate/key match, and
run:

```bash
sudo ln -sfn \
  /etc/nginx/sites-available/kanger-server.conf \
  /etc/nginx/sites-enabled/kanger-server.conf

sudo nginx -t
sudo systemctl restart nginx
sudo systemctl status nginx --no-pager
```

Verify the public route:

```bash
curl --fail --silent --show-error \
  https://api.kanger.org/health
echo
```

The response must contain:

```text
"version":"3.3"
"server_version":"server-0.16"
```

A public request to `/ready` must be rejected with HTTP `403`. The operator port
`1965` must not be reachable publicly at all.

## 9. Deploy the browser UI

Serve the repository `html/` directory from `kanger.org` and optionally
`www.kanger.org`.

Set the explicit browser API endpoint in `html/config.js`:

```javascript
window.KANGER_API_HOST = "https://api.kanger.org";
```

The browser gateway obtains the public auth capability snapshot from `/version`:

```text
TRUSTED        -> Register hidden
EMAIL_VERIFIED -> Register available, confirmation followed by ordinary login
```

Do not add an admin endpoint or owner token to browser configuration. Account
provisioning in TRUSTED mode remains a host operation through `sudo
kanger-admin`.

## 10. Firewall boundary

Allow only:

```text
80/tcp
443/tcp
<SSH_PORT>/tcp
```

Do not allow ports `1964` or `1965` in UFW, provider firewalls, security groups
or container publications.

## 11. Update and rollback

Build and qualify a new JAR, copy it to the VPS, and run the same installer:

```bash
sudo bash /tmp/kanger-deploy/install.sh \
  /tmp/kanger-server.jar
```

Configuration and durable account state are retained. The previous JAR is saved
before restart and restored automatically if application health/readiness fails.

Manual rollback:

```bash
sudo systemctl stop kanger-server.service
sudo cp \
  /opt/kanger-server/kanger-server.jar.previous \
  /opt/kanger-server/kanger-server.jar
sudo chown root:kanger /opt/kanger-server/kanger-server.jar
sudo chmod 0640 /opt/kanger-server/kanger-server.jar
sudo systemctl start kanger-server.service
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

Rollback changes code, not account data. Review migration compatibility before
rolling back across an account-storage format boundary.

## 12. Backup and restore

Durable server state consists of:

```text
/etc/kanger-server/kanger.conf
/var/lib/kanger-server/
```

Back up TLS material separately when nginx stores it outside those roots, for
example:

```text
/etc/nginx/ssl/kanger.org/
```

Create a transactionally quiet filesystem backup:

```bash
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
sudo systemctl stop kanger-server.service
sudo tar -C / -czf "/root/kanger-server-${stamp}.tar.gz" \
  etc/kanger-server \
  var/lib/kanger-server
sudo systemctl start kanger-server.service
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

Copy the archive off the VPS. A backup stored only on the same host is not a
disaster-recovery backup.

Restore:

```bash
sudo systemctl stop kanger-server.service
sudo tar -C / -xzf /root/kanger-server-<timestamp>.tar.gz
sudo chown -R root:kanger /etc/kanger-server
sudo chmod 0750 /etc/kanger-server
sudo chmod 0640 /etc/kanger-server/kanger.conf
sudo chown -R kanger:kanger /var/lib/kanger-server
sudo ln -sfn \
  /etc/kanger-server/kanger.conf \
  /var/lib/kanger-server/kanger.conf
sudo chown -h root:kanger /var/lib/kanger-server/kanger.conf
sudo systemctl start kanger-server.service
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

Never store an unencrypted private key or the owner admin token in the Git
repository.

## 13. Logs and diagnosis

Service state and logs:

```bash
sudo systemctl status kanger-server.service --no-pager
sudo journalctl -u kanger-server.service -n 200 --no-pager
sudo journalctl -u kanger-server.service -f
```

nginx validation and logs:

```bash
sudo nginx -t
sudo systemctl status nginx --no-pager
sudo tail -n 200 /var/log/nginx/error.log
sudo tail -n 200 /var/log/nginx/access.log
```

Listener and routing diagnosis:

```bash
sudo ss -ltnp | grep -E ':(80|443|1964|1965)\b'
sudo docker ps --format \
  'table {{.ID}}\t{{.Names}}\t{{.Ports}}' 2>/dev/null || true
sudo iptables -t nat -S 2>/dev/null \
  | grep -E -- '--dport (443|1964|1965)' || true
```

Interpretation:

- no inbound SYN reaches the VPS: inspect DNS and provider firewall;
- traffic is DNATed to a container: remove or remap that publication;
- nginx receives the request but `/health` fails: inspect the Java service and
  `http://127.0.0.1:1964/health`;
- `kanger-admin` cannot connect: verify the service, port `1965`, owner token
  permissions and `/var/lib/kanger-server` ownership;
- public clients can reach `1964` or `1965` directly: close the firewall or
  binding immediately; nginx is the only public application boundary.

Transport logs contain request id, method, sanitized path, status, duration and
bounded-executor counters. Request bodies, query strings, passwords, session
tokens and owner tokens must not be logged.