# <img src="readme/logo.svg" alt="Hammer Logo" width="48" valign="middle"> Hammer: A story editor

A simple tool for building stories.

![badge-kotlin] ![MIT License](https://img.shields.io/github/license/Darkrock-Studios/hammer-editor) [![Build Status](https://github.com/Darkrock-Studios/hammer-editor/actions/workflows/build.yml/badge.svg)](https://github.com/Darkrock-Studios/hammer-editor/actions/workflows/build.yml) [![Codacy Badge](https://app.codacy.com/project/badge/Grade/6bf82fc32d854a1aa7089615521a3f59)](https://app.codacy.com/gh/Darkrock-Studios/hammer-editor/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![codecov](https://codecov.io/gh/Darkrock-Studios/hammer-editor/graph/badge.svg?token=UDRGLCFZ07)](https://codecov.io/gh/Darkrock-Studios/hammer-editor)

![badge-platform-android] ![badge-platform-ios] ![badge-platform-windows] ![badge-platform-linux] ![badge-platform-macos]

[![Discord badge](https://img.shields.io/discord/1100282852295327744?logo=discord)](https://discord.gg/GTmgjZcupk)
[![Crowdin](https://badges.crowdin.net/hammer-editor/localized.svg)](https://crowdin.com/project/hammer-editor)

### Available on:

| Platform    | App Store                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Direct download                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Android** | [![Google Play](https://img.shields.io/endpoint?color=green&logo=google-play&logoColor=green&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.darkrockstudios.apps.hammer.android%26l%3DGoogle%2520Play%26m%3D%24version)](https://play.google.com/store/apps/details?id=com.darkrockstudios.apps.hammer.android) [![F-Droid](https://img.shields.io/f-droid/v/com.darkrockstudios.apps.hammer.android?logo=FDROID)](https://f-droid.org/en/packages/com.darkrockstudios.apps.hammer.android/) | [![.apk](https://img.shields.io/badge/.apk-3DDC84?logo=android&logoColor=white)](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.apk)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **iOS**     | [![App Store](https://img.shields.io/badge/App_Store-0D96F6?logo=apple&logoColor=white)](https://apps.apple.com/us/app/hammer-editor/id6771650681)                                                                                                                                                                                                                                                                                                                                                     | —                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **Windows** | [![Microsoft Store](https://img.shields.io/badge/Microsoft_Store-0078D4?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0iI2ZmZmZmZiI+PHBhdGggZD0iTTAgMy40NDlMOS43NSAyLjF2OS40NTFIMG0xMC45NDktOS42MDJMMjQgMHYxMS40SDEwLjk0OU0wIDEyLjZoOS43NXY5LjQ1MUwwIDIwLjY5OU0xMC45NDkgMTIuNkgyNFYyNGwtMTIuOS0xLjgwMSIvPjwvc3ZnPg==)](https://apps.microsoft.com/detail/9p84lm8qv5x6)                                                          | [![.msi](https://img.shields.io/badge/.msi-0078D4?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0iI2ZmZmZmZiI+PHBhdGggZD0iTTAgMy40NDlMOS43NSAyLjF2OS40NTFIMG0xMC45NDktOS42MDJMMjQgMHYxMS40SDEwLjk0OU0wIDEyLjZoOS43NXY5LjQ1MUwwIDIwLjY5OU0xMC45NDkgMTIuNkgyNFYyNGwtMTIuOS0xLjgwMSIvPjwvc3ZnPg==)](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.msi) [![.exe](https://img.shields.io/badge/.exe-0078D4?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0iI2ZmZmZmZiI+PHBhdGggZD0iTTAgMy40NDlMOS43NSAyLjF2OS40NTFIMG0xMC45NDktOS42MDJMMjQgMHYxMS40SDEwLjk0OU0wIDEyLjZoOS43NXY5LjQ1MUwwIDIwLjY5OU0xMC45NDkgMTIuNkgyNFYyNGwtMTIuOS0xLjgwMSIvPjwvc3ZnPg==)](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.exe) |
| **macOS**   | [![Mac App Store](https://img.shields.io/badge/Mac_App_Store-000000?logo=apple&logoColor=white)](https://apps.apple.com/us/app/hammer-editor/id6770841038)                                                                                                                                                                                                                                                                                                                                             | [![.dmg](https://img.shields.io/badge/.dmg-000000?logo=apple&logoColor=white)](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.dmg)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Linux**   | [![Snap Store](https://img.shields.io/badge/Snap_Store-82BEA0?logo=snapcraft&logoColor=white)](https://snapcraft.io/hammer-editor)                                                                                                                                                                                                                                                                                                                                                                     | [![.deb](https://img.shields.io/badge/.deb-A81D33?logo=debian&logoColor=white)](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.deb) [![.rpm](https://img.shields.io/badge/.rpm-EE0000?logo=redhat&logoColor=white)](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.rpm) [![AppImage](https://img.shields.io/badge/AppImage-FCC624?logo=linux&logoColor=black)](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.AppImage) [![Flatpak](https://img.shields.io/badge/Flatpak-4A90D9?logo=flatpak&logoColor=white)](https://github.com/Darkrock-Studios/hammer-editor/releases/latest/download/hammer.flatpak)                                                                                                                                                                                                                        |

### Community
Join our [Discord](https://discord.gg/GTmgjZcupk) and help us by reporting bugs, making feature requests, and discussing
the future of Hammer.

Or visit us on Reddit at [r/hammereditor](https://www.reddit.com/r/hammereditor/)

Take a look at the [Roadmap](docs/ROADMAP.md) to see whats coming.

## Multi-platform

This app is where ever you are. Your phone, tablet, desktop, laptop, this program can be installed and run, not simply a
website-in-a-box, but instead using native client side technologies to provide the best experience possible.

## Multi-Screen

Whether you are on a phone, tablet, or desktop, Hammer will make the best use of your screen space.
Also supporting both light and dark modes.

![Screen Shots](readme/preview.png)

## Offline first

I was frustrated with most of the story writing software I was finding as they were using web technologies (_aka: Web
Page in a box_) which always seems to run into problems while being used offline for long periods of time. Hammer is
designed from the ground up to be entirely local, no internet connection required, ever.

## Transparent Data

Your data is yours. It's not stored in the cloud, or some opaque database. It is stored in simple, human readable files,
just using files and folders to define the project structure. You can open your OSes file browser and take a look for
your self. If this program went away today you would be able to easily interact with your data.

## Intelligent Syncing between devices

Your data can be synchronized between devices allowing you to work on your story from anywhere, and have no fear of a
change on one device, overwriting a change on another device.

## Optional and Self hosted Syncing

Syncing is entirely optional, you can install the client and use it on one device, or you can
install the server and sync your data between devices. You could even use some other service to sync
your data, like
Dropbox, Google Drive or [syncthing](https://syncthing.net/). (_however these will only work for
syncing between desktop
computers. Mobile clients will have to use the built in syncing protocol._)

It's all up to you. Instructions can be found [here](docs/HOW-TO-RUN-A-SERVER.md).

If you don't know how, or just don't want to setup your own server, we're also running an official
syncing server.

### Official Server: [hammer.ink](https://hammer.ink/)

This is also the best way to support the project! Subscribe to our [Patreon](https://www.patreon.com/darkrockstudios) in
order to get instant access to this official Sync server.

This official server endeavors to be a reliable, and secure way to sync your projects. We perform regular backups, and
of course encrypt all of our data both in transit, and at rest in our database. Let us manage the server, and you worry
about writing!

#### Test Environment

~~We're also running a test environment here: [test.hammer.ink](https://test.hammer.ink/) which is always running the
latest commit.~~

~~_Do not use the test environment for real data! It will get cleared from time-to-time without notice!_~~

## 🪨 Dark Rock Studios

[**Dark Rock Studios**](https://darkrock.studio/) is all about building **Free and Open Source Software**.

🐛 Found bugs?  
💡 Have suggestions?  
📚 Want to help translate?  
🎮 Interested in our other apps?  
👉 Join our community of Open Source enthusiasts on [**Discord**](https://discord.gg/ju2RQa5x8W)!

# Development

Want to contribute? Great! [Here are some instructions to get you started](DEVELOPMENT.md).

## Having a problem?

Visit our [support](SUPPORT.md) page.

[![Redeploy](https://repology.org/badge/vertical-allrepos/hammer-editor.svg)](https://repology.org/project/hammer-editor/versions)

<!-- TAG_DEPENDENCIES -->

[badge-kotlin]: https://img.shields.io/badge/kotlin-blue.svg?logo=kotlin

<!-- PLATFORMS -->

[badge-platform-linux]: http://img.shields.io/badge/platform-linux-2D3F6C.svg?style=flat

[badge-platform-android]: http://img.shields.io/badge/platform-android-6EDB8D.svg?style=flat

[badge-platform-ios]: http://img.shields.io/badge/platform-ios-CDCDCD.svg?style=flat

[badge-platform-windows]: http://img.shields.io/badge/platform-windows-4D76CD.svg?style=flat

[badge-platform-macos]: http://img.shields.io/badge/platform-macos-111111.svg?style=flat
