#!/usr/bin/env bash
# Prepare the pinned Android SDK without assuming sdkmanager is on PATH.
set -euo pipefail

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${RUNNER_TEMP:-/tmp}/android-sdk}}"
mkdir -p "$sdk_root"
sdk_root=$(cd "$sdk_root" && pwd)
export ANDROID_HOME="$sdk_root"
export ANDROID_SDK_ROOT="$sdk_root"
sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"

if [[ ! -x "$sdkmanager" ]]; then
  download_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/android-cli.XXXXXX")
  trap 'rm -rf "$download_dir"' EXIT
  curl -fL --retry 2 --output "$download_dir/tools.zip" \
    https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  $download_dir/tools.zip" | sha256sum -c -
  unzip -q "$download_dir/tools.zip" -d "$download_dir"
  # Keep any pre-existing tools rather than replacing them silently.
  if [[ -e "$sdk_root/cmdline-tools/latest" ]]; then
    echo 'Existing command-line tools are incomplete; refusing to overwrite them.' >&2
    exit 1
  fi
  mkdir -p "$sdk_root/cmdline-tools"
  mv "$download_dir/cmdline-tools" "$sdk_root/cmdline-tools/latest"
fi

if [[ -n "${GITHUB_ENV:-}" ]]; then
  printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$sdk_root" "$sdk_root" >> "$GITHUB_ENV"
fi
if [[ -n "${GITHUB_PATH:-}" ]]; then
  printf '%s\n%s\n' "$sdk_root/cmdline-tools/latest/bin" "$sdk_root/platform-tools" >> "$GITHUB_PATH"
fi

# yes normally ends with SIGPIPE; sdkmanager's own status is authoritative.
set +e
yes | "$sdkmanager" --sdk_root="$sdk_root" --licenses
license_status=${PIPESTATUS[1]}
set -e
if (( license_status != 0 )); then
  exit "$license_status"
fi

if (( $# == 0 )); then
  set -- 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
fi
"$sdkmanager" --sdk_root="$sdk_root" "$@"
test -x "$sdkmanager"
