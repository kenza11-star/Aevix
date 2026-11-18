# Aevix build guide

Same toolchain as `docs/BUILD.md`: JDK 21, Gradle 9.8.2 (wrapper), AGP 9.4.1, Kotlin 2.4.21, compileSdk 37,
NDK 30.0.16248370, CMake 3.31.6, Ninja, Python 3, clang. About 15 GB of free disk.

## Native dependency provenance

| Dependency | Source | Revision | Status |
|---|---|---|---|
| whisper.cpp | github.com/ggml-org/whisper.cpp | tag `v1.9.5` (from docs/BUILD.md) | Tag name unverified offline; exact gitlink commit not recoverable from the ZIP |
| ncnn (+ glslang) | github.com/Tencent/ncnn | tag `20261020` (from docs/BUILD.md) | Same uncertainty |
| Skia | fetched by `tools/build_skia.sh` | `chrome/m156` @ `95ee33d7ec7791ab9c071675710a14fe6408ae4f` + local patch | Pinned by the script |
| rnnoise, lame, rife_warp | vendored in `third_party/` | in tree | Present |

The ZIP lost the submodule gitlinks. In a real clone from the original repository, `git submodule update --init
--recursive` restores the exact pinned commits and is preferred. If you push this tree to a new repo, add them with
`git submodule add` at the upstream commits and record the SHAs.

## Local build

```
tools/aevix_restore_deps.sh
echo "sdk.dir=/path/to/Android/sdk" > local.properties
tools/build_skia.sh
./gradlew :app:testFossDebugUnitTest --no-daemon
./gradlew :app:assembleFossDebug --no-daemon
```

APK: `android/build/outputs/apk/foss/debug/`. It is signed with the standard debug key. No production secrets are
needed. Release signing stays in the git-ignored `signing.properties`; never commit it.

## CI

`.github/workflows/aevix-android.yml` runs on manual dispatch, pushes to `development`/`develop`, and pull requests.
It restores dependencies, installs the SDK/NDK/CMake, builds Skia, runs unit tests, assembles the debug APK, checks
the package id, label, arm64 libraries and signature, then uploads artifact `Aevix-Android-Debug`. Failures upload
logs as `Aevix-build-logs`. No caching is used yet (Skia is rebuilt each run, which is slow). Action versions are
pinned to the commit SHAs already used by `build.yml`; the workflow has **not been run**.

## Performance procedures (no numbers measured)

Use `tools/perf_bench.py`, `tools/export_speed.sh` and `tools/device_export_check.py` on a physical device, and
Android Studio Profiler for allocations/recomposition.
