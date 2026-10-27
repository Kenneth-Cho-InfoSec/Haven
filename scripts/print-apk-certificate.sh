#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 path/to/release.apk" >&2
    exit 2
fi

if ! command -v apksigner >/dev/null 2>&1; then
    echo "apksigner is required (install Android SDK Build Tools and add it to PATH)." >&2
    exit 127
fi

apksigner verify --print-certs "$1"
