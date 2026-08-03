# KANGER Server VPS deployment

This guide deploys the standalone KANGER Server JAR on a Debian/Ubuntu-style
systemd host behind an existing nginx installation.

Target topology:

```text
Internet
   |
   v
nginx :80/:443
   |
   v
127.0.0.1:1964
   |
   v
KANGER Server JVM managed by systemd
   |
   +-- bounded optional SMTP transport
```

Port `1964` is an internal application port. It must never be opened in the VPS
firewall or bound to a public interface.

## 1. Build and qualify the artifact

On the development machine, from the repository root:

```bash
git fetch origin
git switch server/0.11-mail-transport

mvn -B -ntp \
  -f kanger-server/pom.xml \
  -Dkanger.build.branch.override=deployment \
  clean verify
```

Start the isolated local process:

```bash
bash kanger-server/scripts/run-local.sh
```

In another terminal:

```bash
bash kanger-server/scripts/smoke-local.sh
bash kanger-server/scripts/smoke-auth-local.sh
```

The deployable artifact is:

```text
kanger-server/target/kanger-server.jar
```

## 2. Copy the distribution to the VPS

Replace `user@vps` with the actual SSH destination:

```bash
scp kanger-server/target/kanger-server.jar \
  user@vps:/tmp/kanger-server.jar

scp -r kanger-server/deploy \
  user@vps:/tmp/kanger-deploy
```

The deployment directory contains:

```text
install.sh
verify-installed.sh
kanger.conf.example
systemd/kanger-server.service
nginx/kanger-server.conf.template
```

## 3. Verify Java on the VPS

KANGER Server is qualified on Java 8 and Java 21. Java 21 is recommended for
the service installation.

```bash
java -version
readlink -f "$(command -v java)"
test -x /usr/bin/java
```

When Java is absent on a compatible Debian/Ubuntu release:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless curl
```

Do not continue until `java -version` and `/usr/bin/java` are valid.

## 4. Install the internal service

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
4. preserves the previous JAR as `kanger-server.jar.previous` during updates;
5. installs `/etc/systemd/system/kanger-server.service`;
6. creates `/etc/kanger-server/kanger.conf` only on first installation;
7. links that configuration into the service `user.home`;
8. enables and starts the service;
9. waits for `http://127.0.0.1:1964/health`;
10. restores the previous JAR if the health check fails.

Check the internal endpoint:

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:1964/health
echo
```

Expected response shape:

```json
{"result":"OK","status":"UP","version":"..."}
```

## 5. Review the service configuration

The persistent configuration is:

```text
/etc/kanger-server/kanger.conf
```

It must contain:

```properties
server.bind.address=127.0.0.1
server.port=1964
server.email.mode=disabled
```

After changing configuration:

```bash
sudo systemctl restart kanger-server.service
sudo systemctl status kanger-server.service --no-pager
```

Never change `server.bind.address` to `0.0.0.0` or a public VPS address. nginx is
the public boundary.

## 6. Configure nginx

Choose the public domain:

```bash
DOMAIN=kanger.example.org
```

Copy and render the supplied template:

```bash
sudo cp \
  /tmp/kanger-deploy/nginx/kanger-server.conf.template \
  /etc/nginx/sites-available/kanger-server.conf

sudo sed -i "s/KANGER_DOMAIN/${DOMAIN}/g" \
  /etc/nginx/sites-available/kanger-server.conf
```

Review the certificate paths in the rendered file:

```text
/etc/letsencrypt/live/<domain>/fullchain.pem
/etc/letsencrypt/live/<domain>/privkey.pem
```

They must point to certificates already provisioned for the domain. Do not
enable the HTTPS block until the certificate and key exist.

Enable the site:

```bash
sudo ln -sfn \
  /etc/nginx/sites-available/kanger-server.conf \
  /etc/nginx/sites-enabled/kanger-server.conf

sudo nginx -t
sudo systemctl reload nginx
```

External health check:

```bash
curl --fail --silent --show-error \
  "https://${DOMAIN}/health"
echo
```

## 7. Firewall boundary

Allow only the intended public services and the existing SSH port. Port `1964`
must not be allowed publicly.

Inspect listeners:

```bash
sudo ss -ltnp
```

The KANGER entry must be equivalent to:

```text
127.0.0.1:1964
```

It must not appear as:

```text
0.0.0.0:1964
[::]:1964
```

Run the supplied verification after nginx is configured:

```bash
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

## 8. Enable confirmation mail

Leave mail disabled until the internal service, nginx and `server.url` are
correct. Then follow:

```text
kanger-server/MAIL-CONFIGURATION.md
```

Choose exactly one mode:

```properties
server.email.mode=starttls
```

or:

```properties
server.email.mode=smtps
```

Never enable STARTTLS and implicit TLS simultaneously. The transport uses the
Java platform trust store and hostname verification, finite connection/read/
write timeouts, JavaMail debug off by default, and a bounded worker queue.

Protect the configuration after adding credentials:

```bash
sudo chown root:kanger /etc/kanger-server/kanger.conf
sudo chmod 0640 /etc/kanger-server/kanger.conf
sudo systemctl restart kanger-server.service
```

When `server.email.mode=disabled`, registrations containing a non-empty e-mail
address and resend requests are rejected before the legacy mail paths. Ordinary
password-only registration and authentication remain available.

## 9. Logs and diagnosis

Service state and logs:

```bash
sudo systemctl status kanger-server.service --no-pager
sudo journalctl -u kanger-server.service -n 200 --no-pager
sudo journalctl -u kanger-server.service -f
```

nginx validation and logs:

```bash
sudo nginx -t
sudo tail -n 200 /var/log/nginx/error.log
sudo tail -n 200 /var/log/nginx/access.log
```

## 10. Update and rollback

Build and copy a newly qualified JAR, then run the same installer:

```bash
sudo bash /tmp/kanger-deploy/install.sh \
  /tmp/kanger-server.jar
```

Configuration and user data are retained. The previous JAR is saved before the
service restart. An unsuccessful health check automatically restores it.

Manual rollback:

```bash
sudo systemctl stop kanger-server.service
sudo cp \
  /opt/kanger-server/kanger-server.jar.previous \
  /opt/kanger-server/kanger-server.jar
sudo chown root:kanger /opt/kanger-server/kanger-server.jar
sudo chmod 0640 /opt/kanger-server/kanger-server.jar
sudo systemctl start kanger-server.service
curl --fail http://127.0.0.1:1964/health
```

## 11. Backup

The durable state consists of:

```text
/etc/kanger-server/kanger.conf
/var/lib/kanger-server/
```

For a transactionally quiet filesystem backup:

```bash
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
sudo systemctl stop kanger-server.service
sudo tar -C / -czf "/root/kanger-server-${stamp}.tar.gz" \
  etc/kanger-server \
  var/lib/kanger-server
sudo systemctl start kanger-server.service
curl --fail http://127.0.0.1:1964/health
```

Copy the archive off the VPS. A backup stored only on the same VPS is not a
disaster-recovery backup.

## 12. Restore

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
curl --fail http://127.0.0.1:1964/health
```

## Current deployment boundary

This package qualifies build, bounded loopback HTTP, authentication/session
lifecycle, filesystem input confinement, atomic settings, platform TLS for
outbound HTTP, graceful SIGTERM shutdown, reproducible systemd/nginx deployment,
and bounded explicit confirmation-mail transport.

The historical mail helper remains present for source compatibility, but the
active server request path intercepts new e-mail registrations and resend
requests before those legacy raw-thread branches. A later consolidation slice
may remove that unreachable compatibility code after protocol compatibility is
frozen.
