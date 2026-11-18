#!/usr/bin/env bash
# Restores the git submodules that a GitHub "Download ZIP" source archive drops.
# In a real git checkout `git submodule update --init --recursive` is enough and this script does nothing.
# Revisions come from docs/BUILD.md (whisper.cpp 1.9.5, ncnn 20261020). The exact gitlink commits could not be
# read from the ZIP, so we fetch the upstream release tags; see docs/AEVIX_BUILD_GUIDE.md "Dependency provenance".
set -euo pipefail
cd "$(dirname "$0")/.."

restore() { # path url tag
  local path=$1 url=$2 tag=$3
  if [ -n "$(ls -A "$path" 2>/dev/null)" ]; then echo "$path present, skipping"; return; fi
  if [ -d .git ] && git submodule status -- "$path" >/dev/null 2>&1 && [ -n "$(git submodule status -- "$path")" ]; then
    git submodule update --init --recursive -- "$path"; return
  fi
  rm -rf "$path"; mkdir -p "$(dirname "$path")"
  git clone --depth 1 --branch "$tag" --recurse-submodules --shallow-submodules "$url" "$path"
  echo "$path restored at $(git -C "$path" rev-parse HEAD) (tag $tag)"
}

restore third_party/whisper.cpp https://github.com/ggml-org/whisper.cpp.git v1.9.5
restore third_party/ncnn        https://github.com/Tencent/ncnn.git        20261020
