#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT_DIR/out"
LIB_JAR="$ROOT_DIR/lib/flatlaf-3.4.jar"
MAIN_CLASS="distproject.simulation.ConcurrentOrderingSimulation"

mkdir -p "$OUT_DIR"

echo "Compiling project..."
find "$ROOT_DIR/src" -name '*.java' -print0 | xargs -0 javac -cp "$LIB_JAR" -d "$OUT_DIR"

echo "Running concurrent ordering simulation..."
echo "Tip: start ServerLauncher first, then run this script."
echo "Usage: ./run_simulation.sh [host] [port] [tableNumber] [customerCount] [itemId] [both|add|submit]"
echo

java -cp "$OUT_DIR:$LIB_JAR" "$MAIN_CLASS" "$@"
