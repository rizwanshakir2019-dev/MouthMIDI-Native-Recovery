#!/usr/bin/env bash
# EAS Build pre-install hook — upgrades pnpm to match the local lockfile version.
# Our lockfile is format v9.0 (pnpm 10). Without this, EAS Build's bundled pnpm
# (v8) sees an incompatible lockfile and aborts the frozen-lockfile install.
set -euo pipefail

TARGET_VERSION="10.26.1"

echo "==> Upgrading pnpm to $TARGET_VERSION (required for lockfile v9.0)"
npm install -g "pnpm@$TARGET_VERSION" --silent
echo "==> pnpm $(pnpm --version) ready"
