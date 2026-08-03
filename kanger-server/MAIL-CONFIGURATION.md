# KANGER Server mail configuration

Confirmation mail is disabled by default. The server starts, authenticates
users without e-mail, and passes all local/VPS health checks without SMTP.

Enable mail only after the internal service and the public API URL at
`https://api.kanger.org` are working. The active configuration is
`/etc/kanger-server/kanger.conf` on the VPS or
`kanger-server/run/local/home/kanger.conf` in the local sandbox.

## Security modes

Choose exactly one value:

```properties
server.email.mode=disabled
server.email.mode=starttls
server.email.mode=smtps
```

`starttls` uses SMTP followed by a required STARTTLS upgrade. It commonly uses
port `587`.

`smtps` uses implicit TLS from connection establishment. It commonly uses port
`465`.

The server never enables STARTTLS and implicit TLS simultaneously. Certificate
chain and hostname validation use Java platform defaults; there is no trust-all
or wildcard-trust setting.

## STARTTLS profile

```properties
server.url=https://api.kanger.org
server.email.mode=starttls
server.email.host=smtp.example.org
server.email.port=587
server.email.auth=true
server.email.from=noreply@example.org
server.email.login=noreply@example.org
server.email.password=REPLACE_WITH_REAL_SECRET
server.email.debug=false
server.email.connection.timeout.millis=10000
server.email.read.timeout.millis=10000
server.email.write.timeout.millis=10000
server.email.workers=1
server.email.queue.capacity=64
```

## SMTPS profile

```properties
server.url=https://api.kanger.org
server.email.mode=smtps
server.email.host=smtp.example.org
server.email.port=465
server.email.auth=true
server.email.from=noreply@example.org
server.email.login=noreply@example.org
server.email.password=REPLACE_WITH_REAL_SECRET
server.email.debug=false
server.email.connection.timeout.millis=10000
server.email.read.timeout.millis=10000
server.email.write.timeout.millis=10000
server.email.workers=1
server.email.queue.capacity=64
```

## Apply configuration on the VPS

Edit as root:

```bash
sudoedit /etc/kanger-server/kanger.conf
```

Protect the file:

```bash
sudo chown root:kanger /etc/kanger-server/kanger.conf
sudo chmod 0640 /etc/kanger-server/kanger.conf
```

Restart and inspect startup:

```bash
sudo systemctl restart kanger-server.service
sudo systemctl status kanger-server.service --no-pager
sudo journalctl -u kanger-server.service -n 100 --no-pager
```

The service must still pass its internal health check:

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:1964/health
echo
```

## Functional verification

Create a dedicated test mailbox and register a disposable user through the
normal public API/client with that address.

Expected behavior:

1. registration is accepted only when the address is syntactically valid and
   the mail transport is enabled;
2. the API reports that confirmation mail was queued;
3. the mail is sent by the bounded server mail executor rather than the HTTP
   request thread;
4. the confirmation link uses `server.url` and therefore points to
   `https://api.kanger.org`;
5. resend issues a new one-time confirmation token and queues one message;
6. the previous confirmation token is invalidated by the token store.

Follow logs while testing:

```bash
sudo journalctl -u kanger-server.service -f
```

Keep `server.email.debug=false`. JavaMail debug output can expose protocol and
account details and is not appropriate for normal production operation.

## Queue behavior

`server.email.workers` controls the fixed number of sender threads.
`server.email.queue.capacity` bounds waiting messages.

When the queue is full or shutting down, the API returns a normal KANGER error
instead of creating another thread or allowing an unbounded backlog. On
SIGTERM, the HTTP listener closes first and the mail executor receives up to ten
seconds to drain before the remaining tasks are interrupted.

## Disable mail

To stop accepting e-mail registrations without changing ordinary password-only
login and local qualification:

```properties
server.email.mode=disabled
```

Then restart the service. Registration requests that contain a non-empty e-mail
address and resend requests are rejected before reaching the legacy request
processor; requests without e-mail remain available.
