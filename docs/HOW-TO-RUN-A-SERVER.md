# How to run a Server

So you want to run an Instance of the Hammer Server? Great!

_Note: For now, the server is only available as a Java executable. Eventually we'll add Docker images._

## Getting Started

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

## Enable Community

By default this is disabled. To enable it, add this line to your server config:

```toml
communityEnabled = true
```

This will enable several new pages on the website found at: `/community`

Users will now be able to opt-in to the community if they have already selected a **Pen Name**.
