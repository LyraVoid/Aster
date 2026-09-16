#!/usr/bin/env bash
# Sets the repository secrets the release workflow needs.
#
# The workflow decodes KEYSTORE_BASE64 back into key.jks and writes
# keystore.properties from the other three, so the four are exactly the
# signing setup this clone already has. No secret is stored in this file:
# every value is read out of key.jks / keystore.properties, which this
# repository already ignores.
#
# Note the workflow writes KEYSTORE_FILE=key.jks itself, so that one is not
# a secret. BOT_TOKEN is separate and optional: it only drives the Telegram
# post, and nothing about signing or releasing needs it.
#
# Usage:
#   scripts/set-ci-secrets.sh                 # sets them on LyraVoid/Aster
#   scripts/set-ci-secrets.sh owner/repo      # or on any other repository
set -euo pipefail

REPO="${1:-LyraVoid/Aster}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fail() { echo "$1" >&2; exit 1; }

[ -f key.jks ] || fail "no key.jks here — run this in a clone that can sign"
[ -f keystore.properties ] || fail "no keystore.properties here"
command -v gh >/dev/null || fail "the GitHub CLI (gh) is not installed"
gh auth status >/dev/null 2>&1 || fail "gh is not signed in — run: gh auth login"

# KEYSTORE_FILE is written by the workflow itself, so it is deliberately not read.
# An empty value is refused here rather than sent: `gh secret set` accepts an
# empty stdin happily, so a silently blank secret would only surface later as a
# build that cannot sign.
value_of() {
    local key="$1" line
    line="$(grep "^${key}=" keystore.properties || true)"
    [ -n "$line" ] || fail "keystore.properties has no ${key}"
    [ -n "${line#*=}" ] || fail "keystore.properties has an empty ${key}"
    printf '%s' "${line#*=}"
}

# One line, so `echo "$KEYSTORE_BASE64" | base64 -d` gets the whole thing.
base64 -w0 key.jks > key.jks.base64.txt

gh secret set KEYSTORE_BASE64 --repo "$REPO" < key.jks.base64.txt
value_of KEYSTORE_PASSWORD | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
value_of KEY_ALIAS         | gh secret set KEY_ALIAS         --repo "$REPO"
value_of KEY_PASSWORD      | gh secret set KEY_PASSWORD      --repo "$REPO"

echo
echo "signing secrets set on $REPO:"
gh secret list --repo "$REPO"
