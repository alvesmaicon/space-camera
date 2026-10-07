# Space Camera

**English** · [Português](README.pt-BR.md)

A camera as it should be: simple and effective.

The project began with one concrete need — recording video on a Motorola edge 60 neo
without the automatic zoom the stock camera app forces on you. It grew into a video and
photo app built around manual quality control, exposing in the interface what the stock
camera hides: resolution and frame rate, bitrate, stabilization, HDR, noise reduction
and, in **Pro** mode, ISO and shutter speed.

Kotlin + Jetpack Compose + CameraX. Single module, Android 7.0 (API 24) and up,
targeting API 36. The interface is available in English and Portuguese.

## Features

**Video**
- Resolution and frame rate from what the device actually supports (up to 4K/60 when available)
- Four bitrate presets, capped at 150 Mbps
- EIS stabilization and noise reduction, with per-device support detection
- HDR when the camera HAL offers an HDR scene mode, high-quality tonemap and 10-bit
- Pause and resume while recording
- Optional mirroring for the front camera

**Photo**
- Four quality presets, from 2 MP to the sensor maximum
- Optional adaptive saturation/contrast enhancement after capture
- Flash cycling off → auto → on
- 9:16, 3:4 and Full (crop to the screen) aspect ratios
- EXIF preserved, with optional GPS

**Pro mode**
- Manual ISO and shutter speed on vertical scales, each with its own AUTO
- Ranges come from the device; the ISO scale marks where analog gain ends
- Only offered on devices that report manual sensor control

**Everywhere**
- Mode selector with a "More" drawer; choose which modes stay in the selector, and in which
  order, under Settings → Camera modes
- Zoom dial and lens presets (including ultra-wide when present), tap to focus with exposure
  adjustment, composition grid, horizon level, 3/5/10 s timer
- Adaptive layout for tablets and wide windows
- Settings follow Material 3 with the system's dynamic color

## Getting started

Requirements: **JDK 17+** (tested on 21), **Android SDK with platform 36**, a device or
emulator on API 24+. Node is only needed to regenerate icons.

```bash
cp local.properties.example local.properties   # point sdk.dir to your Android SDK
./gradlew installDebug
```

## Commands

```bash
./gradlew assembleDebug        # debug APK
./gradlew installDebug         # install on the connected device
./gradlew testDebugUnitTest    # unit tests (JVM, no device needed)
./gradlew lint                 # Android Lint
./gradlew detekt               # Kotlin static analysis

scripts/smoke.sh               # build + install + start, checks the camera came up
scripts/logcat.sh              # logcat filtered to the app
npm run generate:all-icons     # regenerate icons from space-cam.png
```

`make help` lists the equivalent shortcuts.

## Diagnosing device-specific problems

Most camera bugs are hardware-specific ("4K/60 doesn't show up", "the photo comes out
dark"). The app logs structured events under a single `SpaceCam` tag:

```bash
scripts/logcat.sh 'evt=caps'   # what the camera HAL reported: EIS, HDR, zoom, resolutions, manual control
scripts/logcat.sh 'evt=bind'   # what was actually applied to the session, with its latency
scripts/logcat.sh 'evt=mode'   # mode changes and the use cases each mode bound
```

If an option doesn't appear on screen, it's because it didn't come back in `evt=caps`.
The About screen shows the commit that produced the installed build, so a report can be
matched to the exact code.

## Architecture in one paragraph

`CameraScreen` → `CameraViewModel` → `CameraController` → CameraX. A camera **mode** (Video,
Photo, Pro) is a declaration in a registry: which CameraX use cases to bind, which hardware
capability it needs, which controls it shows, which aspect-ratio and stabilization rules it
follows, what the shutter does. Nothing branches on "which mode is this" — the screen, the
view model and the controller read the declaration. Adding a mode costs one new file plus
one line in `ModeRegistry`; this was measured, not estimated.

Media is saved to `DCIM/SpaceCamera/` through MediaStore and shows up in the gallery.

## Project documentation

The code identifiers are in English; comments, commit messages and the internal docs are
in **Portuguese**.

| Document | What's in it |
|---|---|
| [CLAUDE.md](CLAUDE.md) | conventions, how to add a mode, testing and device-verification pitfalls |
| [ARCHITECTURE.md](ARCHITECTURE.md) | layers and data flow |
| [BUILD.md](BUILD.md) | toolchain versions, release, CI |
| [ROADMAP.md](ROADMAP.md) | features the app doesn't have yet |
| [REFACTORING.md](REFACTORING.md) | known technical debt and known bugs |
| [specs/](specs/) | specifications with requirements, decisions and on-device verification records |

## Permissions

| Permission | Why |
|---|---|
| `CAMERA` | preview and capture |
| `RECORD_AUDIO` | video audio (not needed with the microphone muted) |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | only if "Save location" is on |
| `READ/WRITE_EXTERNAL_STORAGE` | compatibility with Android 9 and earlier |

## Contributing

Issues and pull requests are welcome, in English or Portuguese — see
[CONTRIBUTING.md](CONTRIBUTING.md). For device-specific bugs, the bug report template asks
for the `evt=caps` line, which usually says more than the description.

## License

[MIT](LICENSE) © 2026 Maicon Alves
