# Artboard preview gallery — setup status

[Artboard](https://github.com/crowded-libs/artboard) is a spatial browser gallery
for Compose Multiplatform `@Preview`s. It discovers stock `@Preview` functions via
KSP, compiles them for a `wasmJs` browser target, and renders them on an
interactive pan/zoom board with stable URLs, search, theming, and PNG export.

Tasks it adds to the module it's applied to:

- `artboardDoctor` — diagnostics
- `artboardReport` — writes `build/reports/artboard/previews.json`
- `artboardRun` / `artboardRunLan` — local gallery server
- `artboardExport` — static production build to `build/artboard/export`

## What has been wired up (this branch)

- `gradle/libs.versions.toml`: `artboard = "0.1.5"` version + `artboard` plugin alias.
- `composeUi/build.gradle.kts`: applies `alias(libs.plugins.artboard)` and declares a
  `wasmJs { browser() }` target (the gallery target Artboard requires).

Versions are compatible: the plugin needs Kotlin ≥ 2.4.0 / Compose 1.11.1; this
project is on Kotlin 2.4.10 / Compose 1.11.1.

## Why this is a spike, not a finished setup

Artboard renders previews that **compile for the `wasmJs` target**. Making that
work in this repo is a multi-module effort, not a config toggle. Three blockers,
all identified by static analysis (the build could **not** be compiled in the web
session — Gradle 9.5.1 is required and its distribution download is denied by the
egress policy, so CI must be the verifier):

### 1. Previews live in the wrong source set

All 132 `@Preview` functions are under `composeUi/src/desktopMain/` and use the
desktop annotation `androidx.compose.ui.tooling.preview.Preview`. A `wasmJs` target
cannot see `desktopMain`. To be discovered they must move to `commonMain` (or a
shared source set that includes `wasmJs`) and switch to the multiplatform
annotation `org.jetbrains.compose.ui.tooling.preview.Preview`.

### 2. The `wasmJs` target cascades through three modules

`composeUi` → `api(project(":common"))` → `api(project(":base"))`. Both `:common`
and `:base` target only android/desktop/iOS today. A `wasmJs` compilation of
`composeUi` forces `wasmJs` variants onto `:common` and `:base`, and onto their
transitive deps (ktor-client — no wasm engine currently wired; okio; koin; napier;
`kmp-zip` + `kmp-zip-okio`; multiplatform-settings; the data layer).

### 3. Some dependencies have no `wasmJs` artifact

Confirmed/likely **blockers** in `composeUi`'s `commonMain`:

- `platform-spellcheckerkt` (native spellcheck backends) — no wasmJs.
- `composetexteditor-spellcheck` — pulls in the above.

**Needs verification** (may or may not publish wasmJs):
`composetexteditor` core + `-find`, `kmpalette-extensions-file`,
`colorpicker-compose`, plus in `:common` the `kmp-zip` family and the ktor engines.

Most other Compose-side deps (Compose MP, decompose, koin, coil, material-kolor,
koalaplot, aboutlibraries, napier, filekit, multiplatform-window-size) do support
wasmJs.

## Suggested phased plan

1. **Isolate a gallery source set.** Rather than making the whole app compile to
   wasm, create a dedicated `wasmJs`-capable preview surface that pulls in only the
   Compose primitives needed to render each component, decoupled from
   `:common`/`:base` data types where possible. This bounds the dependency graph.
2. **Migrate previews to `commonMain`** and the multiplatform `@Preview`
   annotation, one feature area at a time, guarding desktop-only APIs.
3. **Add `wasmJs` to `:base` then `:common`**, resolving each blocking dependency
   (find wasm builds, `expect`/`actual` shims, or exclude from the wasm variant).
4. **Swap the spellcheck editor deps** for wasm-safe variants (or stub the
   spellcheck feature on wasm) so the editor previews compile.
5. **Run `artboardDoctor` / `artboardReport`** in CI to confirm discovery, then
   wire `artboardExport` into a docs/preview publishing job.

Until steps 1–4 land, `wasmJs` build tasks (including `artboard*`) are expected to
fail; the existing android/desktop/iOS builds are unaffected because their
compilations never resolve the `wasmJs` classpath.
