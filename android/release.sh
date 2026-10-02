#!/bin/bash
# release.sh <version> [notes] — one-command Android release.
# Bumps version, pushes (CI builds the APK), waits for green,
# then tags android-v<version>, creates the GitHub release and
# uploads the APK as WatchShark-<version>.apk (the name the
# in-app updater and the website download button expect).
set -e
REPO="Matko802/watchshark"
WF="android-apk.yml"
ART="WatchShark-apk"

VERSION="${1:?usage: release.sh <version> [notes]}"
NOTES="${2:-Release $VERSION}"
CODE="$(python3 -c "m,n,p=map(int,'$VERSION'.split('.')); print(m*10000+n*100+p)")"

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
GRADLE="$ROOT/android/app/build.gradle.kts"

if git -C "$ROOT" rev-parse "android-v$VERSION" >/dev/null 2>&1 || git -C "$ROOT" ls-remote --tags origin "android-v$VERSION" | grep -q .; then
    echo "tag android-v$VERSION already exists, aborting"
    exit 1
fi

sed -i "s/versionCode = [0-9]*/versionCode = $CODE/; s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" "$GRADLE"
grep -n "versionCode\|versionName" "$GRADLE" | head -n 2
git add android/app/build.gradle.kts
git commit -m "Bump to $VERSION" >/dev/null
SHA="$(git rev-parse HEAD)"
git push origin main 2>&1 | tail -n 1

if [ -n "${GH_TOKEN:-}" ]; then
    TOK="x:$GH_TOKEN"
else
    TOK="$(grep -oP 'https://\K[^:]+:[^@]+(?=@github\.com)' ~/.git-credentials | head -n 1)"
fi
API() { curl -s -u "$TOK" "$@"; }

echo "waiting for CI on $SHA..."
RID=""
for _ in $(seq 1 30); do
    RID="$(API "https://api.github.com/repos/$REPO/actions/workflows/$WF/runs?branch=main&per_page=5" | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('workflow_runs',[]):
    if r.get('head_sha','').startswith('$SHA'):
        print(r['id']); break
")"
    [ -n "$RID" ] && break
    sleep 30
done
[ -z "$RID" ] && { echo "CI run never appeared"; exit 1; }
echo "run=$RID"

for _ in $(seq 1 30); do
    ST="$(API "https://api.github.com/repos/$REPO/actions/runs/$RID" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status'), d.get('conclusion'))")"
    echo "$ST"
    case "$ST" in *completed*) break;; esac
    sleep 60
done
[ "$ST" != "completed success" ] && { echo "CI did not succeed ($ST), not tagging"; exit 1; }

AID="$(API "https://api.github.com/repos/$REPO/actions/runs/$RID/artifacts" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['artifacts'][0]['id'])")"
TMP="$(mktemp -d)"
curl -sL -u "$TOK" -o "$TMP/apk.zip" "https://api.github.com/repos/$REPO/actions/artifacts/$AID/zip"
rm -rf "$TMP/out" && mkdir -p "$TMP/out" && unzip -o -q "$TMP/apk.zip" -d "$TMP/out"
APK="$(ls "$TMP/out"/*.apk | head -n 1)"
python3 -c "
import zipfile,sys
z = zipfile.ZipFile('$APK')
assert z.testzip() is None, 'corrupt apk'
m = z.read('AndroidManifest.xml')
assert '$VERSION'.encode('utf-16-le') in m, 'version mismatch'
print('apk ok')
"

git tag "android-v$VERSION" "$SHA"
git push origin "android-v$VERSION" 2>&1 | tail -n 1

REL="$(API -X POST "https://api.github.com/repos/$REPO/releases" -H "Content-Type: application/json" -d "$(python3 -c "import json; print(json.dumps({'tag_name':'android-v$VERSION','name':'Android v$VERSION','body':'''$NOTES'''}))")")"
UPLOAD="$(echo "$REL" | python3 -c "import sys,json; print(json.load(sys.stdin).get('upload_url','').split('{')[0])")"
[ -z "$UPLOAD" ] && { echo "$REL" | head -c 300; exit 1; }
curl -s -u "$TOK" -X POST -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$APK" "$UPLOAD?name=WatchShark-$VERSION.apk" | python3 -c "import sys,json; d=json.load(sys.stdin); print('asset:', d.get('name'), d.get('size'))"
rm -rf "$TMP"
echo "released: https://github.com/$REPO/releases/tag/android-v$VERSION"
