# How to run a Server

So you want to run an Instance of the Hammer Server? Great!

_Note: For now, the server is only available as a Java executable. Eventually we'll add Docker images._

# Linux
1. Download the latest server release:
   2. [ZIP](https://github.com/Wavesonics/hammer-editor/releases/latest/download/server.zip)
   3. [TAR](https://github.com/Wavesonics/hammer-editor/releases/latest/download/server.tar)
4. Extract the archive
5. Create your config file: `serverConfig.toml`
```toml
host = "example.com"
port = 80
serverMessage = "Welcome to the my personal Hammer server!"
contact = "bob@example.com"

# Below this line is optional, if you don't care about HTTPS, just leave it out

sslPort = 443

[sslCert]
path = "/etc/letsencrypt/live/example.com/cert.jks"
storePassword = "1234567890"
keyPassword = "1234567890"
keyAlias = "certificate"
```
6. Now run create a script to run the server: `run.sh`
```bash
#!/bin/bash
cd server
./server --args="--config=../serverConfig.toml"
```
7. Make the script executable: `chmod +x run.sh`
8. Go ahead and test it out: `./run.sh`
9. If everything worked, you should be able to access your set: `http://example.com`
10. **IMPORTANT!** You must now download one of the clients, and create an account on the server. The first account created will be the admin account.
10. (Optional) If you want to set your server up to run automatically, you should configure a systemd service to run it

## Setting up an SSL Certificate (optional)
If you want to enable SSL, you'll need a JKS (_Java Key Store_) certificate. The most common way to get a properly signed certificate is from **Lets Encrypt!** It's free and [relatively easy to setup](https://letsencrypt.org/getting-started/).

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

Once you provide a password it will produce `cert.jks`, this is the file you need to point **Hammer** to in your `serverConfig.toml`.

### Renewing your SSL cert
You can run `sudo certbot renew` which should automatically renew your certificate. `cerbot` needs to bind to port 80 in order to do it, so you may need to shut down the **Hammer** server while it runs.

Once it completes successfully, re-run `convert.sh` to convert the new PEM to JKS, then restart the Hammer server and you should be good to go. 

## Whitelisting Users
By default, the server is closed to everyone after the first account. You can open it by going to `/admin` on the website, logging in as your admin account, and unchecking "Enable White List". Otherwise you can
