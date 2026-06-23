# How to Release

- Make sure your local repository is in a clean state, nothing outstanding
- Change branch to `develop`
- When `develop` is ready to release, run: `./gradlew prepareForRelease`
	- This will prepare your repo by doing the following:
		- Increment app version `app` in `libs.versions.toml`
		- Add new changelog in `fastlane\metadata\android\en-US\changelogs` called `n.txt` where `n` is
		  the android version code
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