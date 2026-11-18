#!/usr/bin/env bash
# Restores the git submodules that a GitHub "Download ZIP" source archive drops.
# Real submodule checkouts retain their recorded commits; ZIP uploads use release tags.
# Revisions come from docs/BUILD.md (whisper.cpp 1.9.5, ncnn 20261020). The exact gitlink commits could not be
# read from the ZIP, so we fetch the upstream release tags; see docs/AEVIX_BUILD_GUIDE.md "Dependency provenance".
set -euo pipefail
cd "$(dirname "$0")/.."

complete() {
  local path=$1
  [ -f "$path/CMakeLists.txt" ] || return 1
  case "$path" in
    */whisper.cpp) [ -f "$path/include/whisper.h" ] && [ -f "$path/ggml/CMakeLists.txt" ] ;;
    */ncnn) [ -f "$path/src/net.h" ] && [ -f "$path/glslang/CMakeLists.txt" ] ;;
    *) return 1 ;;
  esac
}

restore() { # path url tag
  local path=$1 url=$2 tag=$3
  if git ls-files --stage -- "$path" 2>/dev/null | grep -q '^160000 '; then
    git submodule update --init --recursive -- "$path"
    complete "$path" || { echo "Incomplete submodule: $path" >&2; return 1; }
    return
  fi
  if complete "$path"; then echo "$path complete, skipping"; return; fi
  if [ -e "$path/.git" ]; then
    git -C "$path" submodule update --init --recursive
    if complete "$path"; then echo "$path submodules restored"; return; fi
  fi
  if [ -e "$path" ] && { [ ! -d "$path" ] || [ -n "$(ls -A "$path")" ]; }; then
    echo "Incomplete source tree at $path. Restore it from the original dependency before rebuilding; existing files were preserved." >&2
    return 1
  fi
  mkdir -p "$(dirname "$path")"
  local staging
  staging=$(mktemp -d "$(dirname "$path")/.aevix-dep.XXXXXX")
  local staged="$staging/$(basename "$path")"
  if ! git clone --depth 1 --branch "$tag" --recurse-submodules --shallow-submodules "$url" "$staged"; then
    rm -rf "$staging"
    return 1
  fi
  if ! complete "$staged"; then
    echo "Downloaded dependency is incomplete: $path ($tag)" >&2
    rm -rf "$staging"
    return 1
  fi
  [ ! -d "$path" ] || rmdir "$path"
  mv "$staged" "$path"
  rmdir "$staging"
  echo "$path restored at $(git -C "$path" rev-parse HEAD) (tag $tag)"
}

restore third_party/whisper.cpp https://github.com/ggml-org/whisper.cpp.git v1.9.5
restore third_party/ncnn        https://github.com/Tencent/ncnn.git        20261020
