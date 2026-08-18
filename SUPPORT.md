# Hammer Support

Thanks for using Hammer! If you need help, have feedback, or want to request a feature, we'd love to hear from you.

## Get in touch

- **Email:** [darkrockstudios@gmail.com](mailto:darkrockstudios@gmail.com)
- **Chat with us on Discord:** [join discord](https://discord.gg/49Kj5mMj6d)
- **Report a bug or request a feature:** [open an issue on GitHub](https://github.com/Darkrock-Studios/hammer-editor/issues)

We aim to reply to support emails within a few business days.

## Before reaching out

If you're hitting a bug, it helps a lot if you can include:

- What platform you're on (iOS, Android, Desktop)
- The app version
- A short description of what you were doing when the problem happened

## Help translate Hammer

Hammer is community-translated. If you'd like to help bring it to more languages — or improve an existing translation —
join us on Crowdin:

[crowdin.com/project/hammer-editor](https://crowdin.com/project/hammer-editor)

No coding required, and every contribution helps.

## Other resources

- **Source code & releases:** [github.com/Darkrock-Studios/hammer-editor](https://github.com/Darkrock-Studios/hammer-editor)
- **Sync protocol & docs:** see the [`docs/`](docs/) folder in the repo

## Logs

You can find the logs which maybe helful in debugging issues by going to
the About screen, and scrolling to the bottom.

### Where are the logs stored?

On desktop, logs are written to a `logs` folder inside the app's config
directory. The exact location depends on your OS and, on Linux, on how you
installed Hammer (sandboxed packages such as Snap and Flatpak redirect the
config directory).

**Windows:**

`C:\Users\<username>\AppData\Local\DarkrockStudios\hammer\0\logs\`

**macOS:**

`~/Library/Preferences/hammer/0/logs/`

If you installed Hammer from the Mac App Store, it runs sandboxed and that
path is relocated into the app's container:

`~/Library/Containers/com.darkrockstudios.apps.hammer/Data/Library/Preferences/hammer/0/logs/`

**Linux:**

The path depends on the package format you installed:

| Package format        | Logs directory                                                  |
|-----------------------|-----------------------------------------------------------------|
| `.deb`                | `~/.config/hammer/0/logs/`                                       |
| `.rpm`                | `~/.config/hammer/0/logs/`                                       |
| AppImage              | `~/.config/hammer/0/logs/`                                       |
| Snap                  | `~/snap/hammer-editor/current/.config/hammer/0/logs/`           |
| Flatpak               | `~/.var/app/studio.darkrock.hammer/config/hammer/0/logs/`       |

Notes:

- The native packages (`.deb`, `.rpm`, AppImage) follow the
  [XDG Base Directory spec](https://specifications.freedesktop.org/basedir-spec/latest/).
  If you've set `$XDG_CONFIG_HOME`, substitute it for `~/.config` above.
- Snap and Flatpak run sandboxed, so they relocate the config directory
  into their per-app sandbox.

### Mobile

**Android:** Logs aren't browsed directly from a folder. Instead, open the
About screen and use the **Export Logs** button at the bottom — it bundles
the logs into a zip and lets you share them out (e.g. via email or a chat
app) so you can attach them to a bug report.

**iOS:** Hammer doesn't write logs to disk on iOS, so there's nothing to
export or share. If you're hitting a bug on iOS, please describe what
happened and your app version when you reach out.