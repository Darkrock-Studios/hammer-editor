# How to Release

- Make sure your local repository is in a clean state, nothing outstanding
- Change branch to `develop`
- When `develop` is ready to release, run: `./gradlew prepareForRelease`
	- Leave **Auto-publish the GitHub release** unchecked to publish by hand (see
	  [Auto-publishing](#auto-publishing))
	- This will prepare your repo by doing the following:
		- Increment app version `app` in `libs.versions.toml`
		- Add new changelog in `fastlane\metadata\android\en-US\changelogs` called `n.txt` where `n` is
		  the android version code
		- Bake the release notes into the app at
		  `common/src/commonMain/composeResources/files/changelog.md` — this is what the in-app
		  "What's New" dialog shows, so never edit it by hand
		- Merge `develop` into `release`
		- Tag the latest commit to make the release from in the [semvar](https://semver.org) format
		  of `v1.1.1`
		- Push to origin
- This will trigger the `release` action on GitHub which will create a new **Release**, and build
  all the artifacts
- Once the `release` action is complete open the new **Release** on GitHub
- Click _Edit_
- Uncheck "_Set as a pre-release_" and instead check "_Set as the latest release_"
- Click the **Publish Release** button
- This will trigger the `publish` action which will upload artifacts to stores, deploy
  to [hammer.ink](https://hammer.ink), and notify the **Discord** channel of a new release
- All done!

## Auto-publishing

The release dialog's **Auto-publish the GitHub release** checkbox (off by default) does the
manual publish steps above for you. When it is checked `prepareForRelease` appends an
`Auto-Publish: true` trailer to the tag message; once every build job passes, `set-release-body`
strips the trailer from the release body and marks the release as the latest production release,
firing the same `publish` action.

This needs a `RELEASE_PAT` repository secret: a fine-grained PAT scoped to this repo with
**Contents: read and write**. GitHub does not start workflow runs from events triggered by the
default `GITHUB_TOKEN`, so publishing with that token would leave every store untouched. The step
fails with a clear error when the secret is missing.

## Changelog tabs

The release dialog has four changelog tabs. Only **Full** has to be written; the rest
mirror it and are there for when a store needs something different.

| Tab | Goes to | Limit |
| --- | --- | --- |
| **Full** | `CHANGELOG.md`, the GitHub release, the tag message, and the in-app What's New | none |
| **App stores** | Flathub, on releases to every store | none |
| **Google Play** | `fastlane/metadata/android/.../changelogs/<versionCode>.txt`, which F-Droid reads too | 500 |
| **Apple** | `fastlane/metadata/ios/en-US/` and `.../osx/en-US/release_notes.txt` | 4000 |

Each tab follows the one above it until you type in it, after which it says
*Hand-edited* and offers **Re-sync**. So the usual release is one piece of writing on
**Full**; a tab is touched only when that store needs its own wording.

**App stores** is **Full** with the entries tagged for a non-app audience removed
(`[Web]`, `Server:`, a whole `[Server operators]` section, and so on), because Apple
rejects release notes describing anything but the app and Google Play has an equivalent
policy. The tab reports what it took out, since the tag matching is a heuristic and a
silent removal is how an app-facing change goes missing from a listing.

The two store tabs differ in more than length: Google Play's notes carry a link to the
full notes on GitHub, and Apple's must not, because App Store review reads that link as
offering the app outside the App Store and rejects the submission.

Google Play's 500 characters are a hard stop: over the limit, the counter turns red and
**Commit Changes** is disabled. **Trim to fit** drops the auto-fitted text into the
editor as a starting point, so what ships is text someone chose rather than a sentence
cut off at a bullet boundary.

Tabs for stores outside the current publish scope are disabled, and their notes are
dropped on commit. A partial release leaves every notes file it skips untouched: the
iOS and macOS files are written separately even though they share the Apple tab, and
Flathub, which is published by hand with `publishFlathub` rather than by the release
tag, only gets a release entry when every client store is targeted.

## Partial (single-store) releases

Use this when one store needs a hotfix and you don't want to ship the whole matrix.

- The **Tag:** being pushed will have the suffix of the stores to publish to:
	- Full release: `v1.2.4`
	- Single store: `v1.2.4+google-play`
	- Subset: `v1.2.4+google-play+fdroid`
	- Server only: `v1.2.4+server`
- **Server only** ships no client store at all. The server distribution is built and
  attached to the release like always (and deployed to [hammer.ink](https://hammer.ink)
  out of band); the `+server` tag simply matches none of the per-store publish jobs, so
  no app store upload runs.
- The global version in `libs.versions.toml` is still bumped every time, so stores not
  included this cycle catch up on the next full release (their patch number will skip
  forward, which Apple/Google both accept since each store sees a monotonically increasing
  version code).

## Backing out a release (before remote was updated)

If you ran `prepareForRelease` and it failed, leaving things in a half prepared state,
then this ones for you. Run:

```
./gradlew backoutLastRelease
```

- Resets `develop` back to before the "Prepared for release" commit.
- Resets the local `release` branch to `origin/release`.
- Deletes every local tag for this version — both the bare `vX.Y.Z` and any
  `vX.Y.Z+platform+...` partial-release variants.
- **Does NOT touch the remote.** If the push already succeeded, use `revertLastRelease`
  instead.

## Reverting a release that was already pushed

If the prepare push hit origin and you need to undo it:

```
./gradlew revertLastRelease
```

- Force-pushes `release` back to its pre-merge state.
- Force-pushes `develop` back to before the "Prepared for release" commit.
- Deletes the version's tags from **both remote and local** — bare `vX.Y.Z` plus any
  `vX.Y.Z+platform+...` partial-release variants. Remote is deleted first so a failure
  there doesn't leave a stale local tag that could re-push later.
- Anyone who pulled in between will need to reset their local clone.
- **Does NOT undo store uploads.** If the publish workflow already shipped a build to a
  store, you have to roll that back through the store's UI (or by shipping a higher
  version code with the fix).