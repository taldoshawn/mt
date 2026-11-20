#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 --release 17 -d "$out" \
    app/src/main/java/com/mc1h/openfilelab/HexFormatter.java \
    app/src/main/java/com/mc1h/openfilelab/ZipIndex.java \
    app/src/main/java/com/mc1h/openfilelab/DexInspector.java \
    app/src/main/java/com/mc1h/openfilelab/ZipExtractor.java \
    app/src/main/java/com/mc1h/openfilelab/ApkInspector.java \
    tests/SmokeTest.java tests/FeatureTest.java
java -cp "$out" SmokeTest
java -cp "$out" FeatureTest
