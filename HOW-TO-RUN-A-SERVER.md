[# How to run a Server

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

## Whitelisting Users
By default, the server is closed to everyone after the first account. You can open it by going to `/admin` on the website, logging in as your admin account, and unchecking "Enable White List". Otherwise you can
