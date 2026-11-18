# Aevix integration audit

Scope of evidence: AbsentMotion was inspected at file level (Gradle, manifest, updater, CI, docs, source layout).
Open Motion is a single 9.7 MB `index.html` (mostly embedded assets); it was assessed from its README, licence and
keyword/structure analysis of the stripped script, **not** by line-by-line review of its JavaScript. Statuses for Open
Motion are therefore "claimed/indicated", and nothing was compiled or run (see AEVIX_TEST_REPORT.md).

Open Motion's own README labels it "PROTOTYPE / WORK IN PROGRESS, not production-ready".

| Feature | AbsentMotion | Open Motion | Stronger | Strategy | Target files | Verification |
|---|---|---|---|---|---|---|
| Native app, offline | Full Kotlin/Compose app + C++/Skia engine (296 Kotlin, 59 C++ files) | Browser/PWA | AbsentMotion | Keep | whole tree | CI build |
| Multi-layer timeline | `ui/editor/Timeline.kt` (2268 lines), `TimelineMath`, reorder/row-drag | Canvas/DOM timeline, pinch indicated | AbsentMotion | Keep; no port | Timeline.kt | Device test (NOT RUN) |
| Keyframes + interpolation | `model/Keyframes.kt`, `Motions.kt` | Keyframe system indicated (642 keyword hits) | AbsentMotion | Keep | model/Keyframes.kt | Unit tests via CI |
| Cubic Bézier / easing editor | `panels/EasingPanel.kt`, `EasingIcons.kt` | Indicated | AbsentMotion (shared with engine) | Keep; UX ideas only after device review | EasingPanel.kt | Device test (NOT RUN) |
| Vector/point editing | `PointEditor.kt`, `DrawingEditor.kt`, `DrawingMode.kt` (Bézier handles, contours) | README claims anchors, handles, insert, open/closed | AbsentMotion | Keep; avoid second path model | PointEditor.kt | Device test (NOT RUN) |
| 3D / camera | `CameraUi.kt`, model layers, native engine | Camera/perspective indicated | AbsentMotion (native engine) | Keep | CameraUi.kt, shared/cpp | Device test (NOT RUN) |
| Rendering / export | Skia/Vulkan, own encoders (Matroska etc.) | WebGL + MediaRecorder | AbsentMotion | Keep | shared/cpp, export/ | Export check tools |
| Project format | XML scene + `.absproj` packages | localStorage/IndexedDB JSON | AbsentMotion | Keep; format unchanged (`am="app.absentmotion"` marker retained for compatibility) | model/Scene.kt | Existing round-trip tests |
| Effects, audio, AI, captions | Extensive (whisper.cpp, ncnn, rnnoise) | Minimal | AbsentMotion | Keep | effects/, audio/, ai/ | CI |
| PWA / web UI | n/a | Yes | n/a | Not applicable to native | — | — |

## Conclusion

No Open Motion feature was found that is both stronger than the existing native implementation and safely portable
without compiling and testing on a device. **No editor features were ported.** Fabricating a port that could not be
built or run here would risk regressions in a mature codebase. Candidate UX ideas to evaluate on a device later:
timeline pinch-zoom feel, curve-handle hit sizes, mobile layout density.

## Changes actually made

| Area | Change |
|---|---|
| Application ID | `app.absentmotion` -> `app.aevix` (`android/build.gradle.kts`); namespace unchanged |
| Label | Manifest label `Aevix`; Home/About titles and 16 English strings |
| Updater | `Updates.enabled = false` in `UpdateCheck.kt`: Aevix never queries or installs from the AbsentMotion releases (`Comett1225/AbsentMotion`, pinned signer `RELEASE_SIGNER`) |
| apk_check | Expects package `app.aevix` |
| Dependencies | `tools/aevix_restore_deps.sh` restores whisper.cpp v1.9.5 and ncnn 20261020 |
| CI | `.github/workflows/aevix-android.yml`; `build.yml` untouched |
| Licences | `third_party/OPEN_MOTION_LICENSE.txt` (MIT) added; AbsentMotion GPL-3.0, NOTICE and third-party notices untouched |

## Identity items deliberately left unchanged

- Kotlin namespace `app.absentmotion` (rename risk, per the brief).
- Storage folder name `AbsentMotion` (`ExportTarget.AppFolder`) and strings that mention it, to avoid orphaning data.
- Non-English strings still say AbsentMotion; the About/independence/compatibility notes keep the original name for credit.
- Launcher icons not redrawn (no new artwork supplied).
- Other `tools/*.py` device scripts still use the old package id for adb.
- Project-file marker `am="app.absentmotion"` kept so files stay compatible.
