#!/usr/bin/env bash
# Turns a release tag into Gradle version properties.
#
#   usage: version-from-tag.sh v1.2.3[-beta.4]
#   prints (and appends to $GITHUB_ENV when set):
#     VERSION_NAME=1.2.3[-beta.4]
#     VERSION_CODE=<int>
#
# versionCode must only ever go UP, and stable / pre-release builds come from two
# different workflows (so their run numbers can't be compared). It is therefore derived
# from the tag itself:
#
#   code = (MAJOR*10000 + MINOR*100 + PATCH) * 100 + stage
#   stage: stable = 99 ; alpha.N = 0..29 ; beta.N = 30..59 ; rc.N = 60..89
#
# so 1.0.0-alpha.1 < 1.0.0-beta.6 < 1.0.0-rc.1 < 1.0.0 < 1.0.1-beta.1.
set -euo pipefail

tag="${1:?usage: version-from-tag.sh vX.Y.Z[-suffix]}"

if [[ ! "$tag" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)(-([0-9A-Za-z.]+))?$ ]]; then
  echo "Invalid tag: $tag (expected vX.Y.Z or vX.Y.Z-suffix)" >&2
  exit 1
fi

major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[2]}"; patch="${BASH_REMATCH[3]}"
suffix="${BASH_REMATCH[5]:-}"

if (( minor > 99 || patch > 99 || major > 2000 )); then
  echo "Version out of range for versionCode packing: $tag" >&2
  exit 1
fi

if [[ -z "$suffix" ]]; then
  stage=99
else
  # Last number in the suffix is the iteration (beta.4 -> 4); none means 0.
  n=0
  if [[ "$suffix" =~ ([0-9]+)[^0-9]*$ ]]; then n=$(( 10#${BASH_REMATCH[1]} )); fi
  (( n > 29 )) && n=29
  case "${suffix,,}" in
    alpha*) stage=$(( 0 + n )) ;;
    beta*)  stage=$(( 30 + n )) ;;
    rc*)    stage=$(( 60 + n )) ;;
    *)      stage=$(( 30 + n )) ;;   # unknown label: treat like a beta
  esac
fi

code=$(( (major * 10000 + minor * 100 + patch) * 100 + stage ))
name="${tag#v}"

echo "VERSION_NAME=$name"
echo "VERSION_CODE=$code"
if [[ -n "${GITHUB_ENV:-}" ]]; then
  { echo "VERSION_NAME=$name"; echo "VERSION_CODE=$code"; } >> "$GITHUB_ENV"
fi
