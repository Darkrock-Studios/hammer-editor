# How to run a Sync Server

## ⚠️ This is intended for advanced Users

_Running a server is an inherently technical process, if you have never run a server accessible on
the internet, this is probably not for you._

## Getting Started
So you want to run an Instance of the Hammer Server? Great!

_Note: For now, the server is only available as a Java executable. Eventually we'll add Docker images._

The Hammer server is a Java application that runs on Windows, Linux, and macOS.

1. Download the latest server release:
	- [ZIP](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/server.zip)
	- [TAR](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/server.tar)
2. Extract the archive to your desired location
3. Create your config file: `config.toml`. The server automatically loads a `config.toml` placed in its data directory (`~/hammer_data/`), so you don't need to pass `--config` if you put it there. To keep it elsewhere, pass its path with `--config` (see the platform-specific scripts below). It is strongly advised to use a port other than `80`, unless this is the only web-based program running on the system. If you intend to access the server by the host FQDN (e.g. `hammer.example.com`), make sure to set this in your DNS records; otherwise this will only be accessible via IP (e.g. `10.1.1.1`, `192.0.2.1`).

   ```toml
   host = "example.com"
   port = 80
   ```

4. Run the server (_see platform-specific instructions below_)
5. If everything worked, you should be able to access your server at your server's IP or at the host name you set in your config file and DNS configuration, such as:
   `http://example.com`
6. **IMPORTANT!** You must now download one of the clients and create an account on the server. The first account
   created will be the admin account.

## Network binding

By default the server binds to all IPv4 interfaces (`0.0.0.0`), so it accepts connections from the
network. To isolate it — for example when a reverse proxy on the same host is the only thing that
should reach it — restrict the bind to loopback with `bindHosts`:

```toml
# Accept loopback connections only (IPv4 and IPv6). Default is ["0.0.0.0"].
bindHosts = ["127.0.0.1", "::1"]
```

`bindHosts` is the network interface(s) to listen on, and is distinct from `host`, which is the
public name shown to users. Each address gets its own listener (and its own HTTPS listener when an
SSL cert is configured).

## Rich link previews (optional)

By default, sharing an author or story link on social media shows a branded static preview card.
To instead generate a **per-page** share image — the story title or author name rendered onto the
card — enable `richLinkPreviews`:

```toml
richLinkPreviews = true
```

Generated images are disk-cached under `~/hammer_data/cache/og/` and pruned automatically.

**Requirement:** rendering the text uses headless AWT, which needs native font libraries installed.
On Debian/Ubuntu:

```sh
sudo apt-get install -y fontconfig libfreetype6
```

Without them, leave `richLinkPreviews` off (the default): link previews still work via the static
cards, so the out-of-the-box setup needs nothing extra.

## Storage

The server persists its data in a PostgreSQL database. It supports two modes:

- **Embedded (default):** An in-process PostgreSQL server is started automatically. Data lives under
  `~/hammer_data/pgdata/`. No external services required — drop the JAR on a box and run it.
- **Remote:** Point at an externally-managed PostgreSQL server. Use this when you outgrow embedded
  or want managed backups.

Embedded is the default; no config is needed for it. This is the intended mode for self-hosters.
To override the embedded port, or to switch to remote, add a `[storage]` block to
`config.toml`:

```toml
# Embedded (defaults shown). Omit this whole block to accept the defaults.
[storage]
type = "embedded"
[storage.embedded]
port = 54329        # pinned for predictability
dataDirName = "pgdata"
```

```toml
# Remote.
[storage]
type = "remote"
[storage.remote]
host = "db.example.com"
port = 5432
database = "hammer"
user = "hammer"
password = "..."
schema = "public"
poolSize = 10
useSsl = true
```

### Upgrading from a pre-PostgreSQL version

Older releases (prior to v3.1.0) used SQLite (`~/hammer_data/server.db`). The first run after
upgrading auto-detects the file and migrates its contents into PostgreSQL inside a single
transaction, then renames the source to `server.db.migrated-<timestamp>.bak`. If migration fails for
any reason, the SQLite file is left untouched and the server exits with an error — fix the cause and
start again.

To rehearse the migration against a copy of production before flipping the live config, run with `--migrate-dry-run`: it does everything except commit and rename.

## Encryption at rest

Content encryption at rest is **optional** — a fresh server stores in plaintext and
needs no key material. This is recommended for most self-hosters. Encryption is
slower, and carries the possibility of total data loss if key material is mishandled.

If you want to enable at-rest encryption, see
[Encryption at rest & key management](SERVER-SECRET-STORAGE.md) for the
full walkthrough.

## Platform-Specific Instructions

### Linux

Create a script to run the server in the top level of the installation directory (e.g. `hammer/`): `run.sh`
```bash
#!/bin/bash
./bin/server --config config.toml
```

Make the script executable:

```bash
chmod +x run.sh
```

Run the server:

```bash
./run.sh
```

(Optional) To set up the server to run automatically, configure a systemd service.

#### SystemD Service Example

You will want to create a system user without login ability for security purposes.

```
sudo adduser hammer --disabled-login
```

And then make sure to change ownership for the installation directory to the new user.

```
sudo chown -R hammer:hammer hammer/
```

Finally, create the `hammer.service` file in your `systemd/system` folder. Be sure to change the `[installation directory]` to your directory.

```
[Unit]
Description=Hammer Server for Story Editing
After=network.target postgresql.service

[Service]
User=hammer
Group=hammer

Type=simple

WorkingDirectory=[installation directory]
ExecStart=[installation directory]/run.sh
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### Windows

Create a script to run the server in the top level of the installation directory (e.g. `hammer\`):
`run.bat`

```batch
@echo off
bin\server.bat --config config.toml
```

Run the server:

```batch
run.bat
```

### macOS

Create a script to run the server in the top level of the installation directory (e.g. `hammer/`):
`run.sh`

```bash
#!/bin/bash
./bin/server --config config.toml
```

Make the script executable:

```bash
chmod +x run.sh
```

Run the server:

```bash
./run.sh
```

## Setting up SSL (required for direct connections)

**Hammer clients only speak `https`.** A client will never connect over plain HTTP, so a
server that clients reach **directly** must terminate TLS. The only deployment that can run
Hammer on plain HTTP is one sitting **behind a reverse proxy** — the proxy holds the
certificate and forwards plain HTTP to Hammer on the same host.

There are two ways to serve Hammer over `https`. Pick **one**:

- **Java SSL (this section):** Hammer terminates TLS itself, reading your certificate
  directly. Simplest if Hammer is the only thing on the box.
- **Reverse proxy:** A proxy such as Nginx terminates TLS and forwards plain HTTP to
  Hammer. Preferred if you run other services on the same host. If you go this route,
  **skip the rest of this section** and follow
  [Reverse Proxy using Nginx](#reverse-proxy-using-nginx) instead.

> The plain HTTP connector (`port`, default 8080) still exists solely for the reverse-proxy
> case. Do not expose it directly to clients — they won't use it.

If you want Hammer to terminate SSL itself, you'll first need to edit your server config file and
add these lines:

```toml
sslPort = 443
forceHttps = true # Optional, defualts to true
```

### Getting an SSL Certificate

#### Self-signed certs are not supported in production

Don't use a self-signed certificate for a real deployment. The mobile clients trust only the
system CA store — Android won't trust user-added or self-signed CAs by default, and iOS rejects
them too — so they'll fail the TLS handshake with no way to override it. The desktop client also
rejects self-signed certs outside development mode. Use a real certificate from **Let's Encrypt**
below (or put Hammer behind a reverse proxy that holds one).

#### Development: automatic self-signed cert

When you run the server with `--dev` and **no** `sslCert` configured, Hammer generates a
self-signed certificate on first boot and serves it, persisting the keystore to
`hammer_data/dev-selfsigned.jks` so it stays stable across restarts. To avoid the privileged
port 443 (which needs root on Linux/macOS), the dev connector listens on **8443** by default;
set `sslPort` to override. Point the client at `localhost:8443` or `127.0.0.1:8443`.

The desktop client run with `--dev` trusts this self-signed cert, but **only for loopback hosts**
(localhost / 127.0.0.1) — a `--dev` client talking to any other host still does full certificate
and hostname validation, so pointing a dev build at a real server is not silently insecure.

This path never activates without `--dev`; a production server with no `sslCert` serves plain HTTP
only (for the reverse-proxy case). Mobile clients still won't trust the self-signed cert, so
develop the mobile clients against a real certificate or a reverse proxy.

#### Let's Encrypt

The most common way to get a properly signed certificate is from **Lets Encrypt!** It's free
and [relatively easy to setup](https://letsencrypt.org/getting-started/).

Hammer can accept certificates in two formats:

- PEM - _Privacy Enhanced Mail_ (recommended — what certbot/Let's Encrypt produces directly)
- JKS - _Java Key Store_ (legacy)

#### PEM (recommended)

Once you've set it up, Let's Encrypt will give you a directory of PEM files such as
`/etc/letsencrypt/live/example.com`. The two files we care about are `fullchain.pem` and
`privkey.pem`.

Point **Hammer** straight at them in your `config.toml` — no conversion needed:

```toml
[sslCert]
certChainPath = "/etc/letsencrypt/live/example.com/fullchain.pem"
privateKeyPath = "/etc/letsencrypt/live/example.com/privkey.pem"
```

#### JKS (legacy)

Hammer also accepts a Java Key Store. You can convert the PEM files above into a `cert.jks` with
this script:

`convert.sh`
```shell
#!/bin/sh
openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out certificate.p12 -name "certificate"
keytool -importkeystore -srckeystore certificate.p12 -srcstoretype pkcs12 -destkeystore cert.jks
```

Once you provide a password it will produce `cert.jks`. Point **Hammer** at it:

```toml
[sslCert]
path = "/etc/letsencrypt/live/example.com/cert.jks"
storePassword = "1234567890"
keyPassword = "1234567890"
keyAlias = "certificate"
```

### Renewing your SSL cert

This applies to the **Java SSL** method, where Hammer terminates TLS and holds the HTTP port. If
you run behind a reverse proxy, the proxy owns ports 80/443 and renews its certificate without ever
touching Hammer, so none of the stop/start dance below is needed.

`certbot renew` renews the certificate in place, rewriting `fullchain.pem` and `privkey.pem`. Two
things to know:

- Because **Hammer** holds the HTTP port, certbot's standalone challenge needs that port free, so
  Hammer must be
  stopped while certbot runs.
- A running server reads its certificate only at startup, so it won't pick up a renewed cert until
  it restarts.

Configure both as certbot renewal hooks so the renewal that runs automatically handles everything.
Add them to the
cert's renewal config, `/etc/letsencrypt/renewal/<your-domain>.conf`, under the `[renewalparams]`
section:

```ini
pre_hook = systemctl stop hammer
post_hook = systemctl start hammer
```

These hooks run **only** when a renewal actually happens, not on the routine no-op checks, so Hammer
is stopped only
when a new cert is genuinely being issued.

> **Note:** You can also pass `--pre-hook`/`--post-hook` on the command line, but certbot only
> persists them into the
> config when a renewal actually occurs — so setting them while no renewal is due (the common case)
> silently does
> nothing. Editing the config directly, as above, always works.

When Hammer points at the PEM files directly (recommended), the `post_hook` restart is all that's
needed to pick up
the new cert — there's no conversion step.

Renewals fire automatically on a timer that certbot installs. Confirm it's active:

```shell
systemctl list-timers | grep certbot
```

The timer's unit name depends on how certbot was installed — `certbot.timer` (apt/dnf) or
`snap.certbot.renew.timer` (snap) — which is why grepping `list-timers` is the reliable check.

## Reverse Proxy using Nginx

Instead of having Hammer terminate TLS itself (the [Setting up SSL](#setting-up-ssl-optional)
section above), you can put it behind a reverse proxy that terminates TLS and forwards plain HTTP to
Hammer. This is good practice — especially when other services share the host — and is documented
here for Nginx. Use **either** this approach **or** Java SSL, not both.

### Changes to config.toml

Example port used. (If you're running multiple services on a webserver, you've probably already used 8080.) For security purposes, make sure to set `bindHosts` as below (V => 3.4.0).

```toml
port = 8200
bindHosts = ["127.0.0.1", "::1"]
```

Make sure to update your DNS with your desired URL to be able to use LetsEncrypt and the like.

### Base Nginx Config

Create your base file. Make sure to change `hammer.example.com` to your domain!

`nano /etc/nginx/sites-available/hammer`

```nginx
server {
	listen 80;
	server_name hammer.example.com;

    location / {
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_pass http://localhost:8200;
    }
}
```

### HTTPS

See LetsEncrypt or your favorite SSL provider for an SSL certificate. Once that is completed, install said new cert into your Nginx file. Be sure to set the proper path for your certificate fullchain and private key. Once again, make sure to change `hammer.example.com` to your domain in the `server_name` lines and the `ssl_certificate*` lines. If LetsEncrypt installs the HTTP to HTTPS redirect itself, make sure your file roughly reflects this example below.

```nginx
server {
	listen 80;
	server_name hammer.example.com;
	return 301 https://$host$request_uri;
}

server {
	listen 443 ssl http2;
	ssl_protocols TLSv1.2 TLSv1.3;
	ssl_ciphers TLS_AES_256_GCM_SHA384:TLS_AES_128_GCM_SHA256:TLS_CHACHA20_POLY1305_SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305;
	ssl_session_cache shared:SSL:10m;
	ssl_session_timeout 1d;
	add_header X-Frame-Options "SAMEORIGIN" always;
	add_header X-Content-Type-Options "nosniff" always;
	add_header 'Referrer-Policy' 'same-origin';
	ssl_certificate /etc/letsencrypt/live/hammer.example.com/fullchain.pem;
	ssl_certificate_key /etc/letsencrypt/live/hammer.example.com/privkey.pem;

	server_name hammer.example.com;

	location / {
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_pass http://localhost:8200;
    }
}
```

### Link, Test, Reload

Link the file to the `sites-enabled` folder.

```sh
ln -s /etc/nginx/sites-available/hammer /etc/nginx/sites-enabled
```

Make sure to test your configuration!

```sh
sudo nginx -t
```

As long as that doesn't throw any errors, you can restart nginx. You should now be able to access your Hammer server web page at your URL!

## Whitelisting Users

By default, the server is closed to account creation after the first account.

You can open it by going to `/admin` on the website, logging in as your admin account, and clicking
"**Disable Whitelist**".
Otherwise, you can add individual users to the whitelist using the admin page.

_**Note:** Disabling the whitelist is **strongly discouraged**. There are currently no moderation
tools or even account
verification. I expect any fully open server would become filled with spam very quickly._

## Enable Community

By default this is disabled. To enable it, add this line to your server config:

```toml
communityEnabled = true
```

This will enable several new pages on the website found at: `/community`

Users will now be able to opt-in to the community if they have already selected a **Pen Name**.

## Setup Email Sending (_Optional_)

Currently, we mainly use Email for password reset. Eventually we maybe have account verification,
and potentially other
things we send emails for.

There are several supported ways to send emails:

- SMTP - Standard Email
- Mailgun - https://www.mailgun.com/
- Sendgrid - https://sendgrid.com
- Postmark - https://postmarkapp.com/

You can configure the email provider by first selecting which you want to use in your
`config.toml` file:

```toml
emailProvider = "SMTP" 
```

Then restart your server and navigate to the admin page to configure your email settings.

Only SMTP has been thoroughly tested so far.

## Web Analytics (_Optional_)

You can opt into a web analytics provider to measure traffic to your server's
public web pages.

Analytics is only served on **public (logged-out) pages**. It is never injected
into the dashboard, story, or admin pages of signed-in users.

By default analytics is disabled (`type = "none"`). Supported providers are
[Umami](https://umami.is) and [Google Analytics](https://analytics.google.com).

Both providers report a `download` event, broken down by `platform` and `format`,
when a visitor clicks a download button on the home page.

### Umami

Create a website in your Umami dashboard, copy its **Website ID**, and add:

```toml
[analytics]
type = "umami"

[analytics.umami]
websiteId = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
# scriptUrl defaults to Umami Cloud. To use a self-hosted Umami instance,
# point it at your own script, e.g.:
scriptUrl = "https://umami.example.com/script.js"
# connectSrc overrides the CSP connect-src event hosts. Umami Cloud's script
# POSTs events to a gateway origin that has moved several times; if tracking
# is blocked by CSP after a host change, set the current host(s) here to fix
# it without a code release, e.g.:
connectSrc = ["https://gateway.umami.is"]
```

### Google Analytics

Create a GA4 property, copy its **Measurement ID** (`G-XXXXXXXXXX`) from the data
stream, and add:

```toml
[analytics]
type = "google"

[analytics.google]
measurementId = "G-XXXXXXXXXX"
```

The `platform` and `format` event parameters are sent automatically, but GA4 only
shows them in reports once you register them as **custom dimensions** in the GA
admin console.

The configuration is designed to grow: support for additional providers can be
added under the `[analytics]` section in the future by selecting a different
`type`.
