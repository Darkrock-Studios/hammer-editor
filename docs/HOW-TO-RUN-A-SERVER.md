# How to run a Sync Server

## ⚠️ This is intended for advanced Users

_Running a server is an inherently technical process, if you have never run a server accessible on
the internet, this is probably not for you._

## Getting Started
So you want to run an Instance of the Hammer Server? Great!

_Note: For now, the server is only available as a Java executable. Eventually we'll add Docker images._

The Hammer server is a Java application that runs on Windows, Linux, and macOS.

1. Download the latest server release:
	- [ZIP](https://github.com/Wavesonics/hammer-editor/releases/latest/download/server.zip)
	- [TAR](https://github.com/Wavesonics/hammer-editor/releases/latest/download/server.tar)
2. Extract the archive to your desired location
3. Create your config file: `serverConfig.toml` in a location of your choice

   ```toml
   host = "example.com"
   port = 80
   ```

4. Run the server (_see platform-specific instructions below_)
5. If everything worked, you should be able to access your server at the host name you set in your config, such as:
   `http://example.com`
6. **IMPORTANT!** You must now download one of the clients and create an account on the server. The first account
   created will be the admin account.

## Storage

The server persists its data in a PostgreSQL database. It supports two modes:

- **Embedded (default):** An in-process PostgreSQL server is started automatically. Data lives under
  `~/hammer_data/pgdata/`. No external services required — drop the JAR on a box and run it.
- **Remote:** Point at an externally-managed PostgreSQL server. Use this when you outgrow embedded
  or want managed backups.

Embedded is the default; no config is needed for it. This is the intended mode for self-hosters.
To override the embedded port, or to switch to remote, add a `[storage]` block to
`serverConfig.toml`:

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

If you want to enable at-rest encryption, see *
*[Encryption at rest & key management](SERVER-SECRET-STORAGE.md)** for the
full walkthrough.

## Platform-Specific Instructions

### Linux

Create a script to run the server: `run.sh`
```bash
#!/bin/bash
cd server
./server --args="--config /some/path/serverConfig.toml"
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

Create a script to run the server: `run.bat`

```batch
@echo off
cd server
server.bat --args="--config C:\path\to\serverConfig.toml"
```

Run the server:

```batch
run.bat
```

### macOS

Create a script to run the server: `run.sh`

```bash
#!/bin/bash
cd server
./server --args="--config /path/to/serverConfig.toml"
```

Make the script executable:

```bash
chmod +x run.sh
```

Run the server:

```bash
./run.sh
```

## Setting up SSL (optional)

_This step is optional but strongly recommended._

If you want to enable SSL (`https`), you'll first need to edit your server config file and add these lines:

```toml
sslPort = 443
forceHttps = false # Optional, defualts to true
```

### Getting an SSL Certificate

#### Self-Signed

_TODO: Add instructions for self-signed certs_

#### Let's Encrypt

The most common way to get a properly signed certificate is from **Lets Encrypt!** It's free
and [relatively easy to setup](https://letsencrypt.org/getting-started/).

Hammer can accept certificates in two formats:

- JKS - _Java Key Store_
- PEM - _Privacy Enhanced Mail_

PEM support is brand new, JKS is well-tested.

#### JKS
Once you've set it up, Lets Encrypt will give you a bunch of PEM files in a directory such as:
`/etc/letsencrypt/live/example.com`

The two files we really care about are `fullchain.pem` and `privkey.pem`.

We can use these two to produce a JKS file, here is a script that will help you do it:

`convert.sh`
```shell
#!/bin/sh
openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out certificate.p12 -name "certificate"
keytool -importkeystore -srckeystore certificate.p12 -srcstoretype pkcs12 -destkeystore cert.jks
```

Once you provide a password it will produce `cert.jks`, this is the file you need to point **Hammer** to in your
`serverConfig.toml`.

Finally, you will add these lines to your config file:

```toml
[sslCert]
path = "/etc/letsencrypt/live/example.com/cert.jks"
storePassword = "1234567890"
keyPassword = "1234567890"
keyAlias = "certificate"
```

#### PEM

PEM support is brand new but should shortcut the process described above.

Now you can simply give the PEM files directly to **Hammer** in your `serverConfig.toml`:

```toml
[sslCert]
certChainPath = "/etc/letsencrypt/live/example.com/fullchain.pem"
privateKeyPath = "/etc/letsencrypt/live/example.com/privkey.pem"
```

### Renewing your SSL cert

You can run `sudo certbot renew` which should automatically renew your certificate. `cerbot` needs to bind to port 80 to
do it, so you may need to shut down the **Hammer** server while it runs.

Once it completes successfully, re-run `convert.sh` to convert the new PEM to JKS, then restart the Hammer server, and
you should be good to go.

## Whitelisting Users

By default, the server is closed to account creation after the first account.

You can open it by going to `/admin` on the website, logging in as your admin account, and clicking
"**Disable Whitelist**".
Otherwise, you can add individual users to the whitelist using the admin page.

_**Note:** Disabling the whitelist is **strongly discouraged**. There are currently no moderation tools or even account
verification. I expect any fully open server would become filled with spam very quickly._

## Enable Community

By default this is disabled. To enable it, add this line to your server config:

```toml
communityEnabled = true
```

This will enable several new pages on the website found at: `/community`

Users will now be able to opt-in to the community if they have already selected a **Pen Name**.

## Setup Email Sending (_Optional_)

Currently, we mainly use Email for password reset. Eventually we maybe have account verification, and potentially other
things we send emails for.

There are several supported ways to send emails:

- SMTP - Standard Email
- Mailgun - https://www.mailgun.com/
- Sendgrid - https://sendgrid.com
- Postmark - https://postmarkapp.com/

You can configure the email provider by first selecting which you want to use in your `serverConfig.toml` file:

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

By default analytics is disabled (`type = "none"`). Currently, the only supported
provider is [Umami](https://umami.is).

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

The configuration is designed to grow: support for additional providers can be
added under the `[analytics]` section in the future by selecting a different
`type`.
