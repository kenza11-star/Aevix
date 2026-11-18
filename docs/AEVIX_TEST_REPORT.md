# Aevix test report

Environment: sandbox with Python 3 and git only. No JDK, Android SDK/NDK, Gradle, or network access.

| Check | Result |
|---|---|
| Archives extracted and inspected | PASS |
| `py_compile tools/apk_check.py` | PASS |
| `bash -n tools/aevix_restore_deps.sh` | PASS |
| YAML parse of `aevix-android.yml` | PASS |
| XML well-formedness of manifest and `values/strings.xml` | PASS |
| Baseline AbsentMotion build | NOT RUN |
| Skia build / native C++ compile | NOT RUN |
| Kotlin compile (incl. `UpdateCheck.kt` edit) | NOT RUN |
| `:app:testFossDebugUnitTest` | NOT RUN |
| `:app:assembleFossDebug` / APK verification | NOT RUN |
| GitHub Actions run | NOT RUN |
| Emulator/device tests, export, save/load round trip | NOT RUN |
| Open Motion feature ports | NOT DONE (none ported) |

## Risks to check first on CI

1. Release tags `v1.9.5` / `20261020` may not match the original submodule commits.
2. Kotlin builds use `allWarningsAsErrors`; the new `enabled` flag is a plain `var` to avoid constant-condition warnings, but this is unconfirmed.
3. Unit tests that assert the old package id may need review (`UpdateVerifyTest` builds its own fixtures, so it should be unaffected).
4. Unit tests or resource checks referencing the changed English strings.
5. Debug manifest requests `ACCESS_LOCAL_NETWORK` (existing; unchanged).
