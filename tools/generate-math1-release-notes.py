#!/usr/bin/env python3
"""Generate MATH1-compatible GitHub Release notes and metadata."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("apk", type=Path)
parser.add_argument("--version-name", required=True)
parser.add_argument("--version-code", required=True, type=int)
parser.add_argument("--min-sdk", required=True, type=int)
parser.add_argument("--compile-sdk", type=int)
parser.add_argument("--target-sdk", type=int)
parser.add_argument("--channel", choices=("stable", "beta", "alpha"), default="stable")
parser.add_argument("--v-version")
parser.add_argument("--notes", default="")
args = parser.parse_args()

if not args.apk.is_file() or args.apk.stat().st_size <= 0 or args.apk.suffix.lower() != ".apk":
    raise SystemExit("APK must be a non-empty .apk file")
if args.version_code <= 0 or not 1 <= args.min_sdk <= 100:
    raise SystemExit("versionCode must be positive and minSdk must be between 1 and 100")

digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
metadata = {
    "versionName": args.version_name,
    "versionCode": args.version_code,
    "minSdk": args.min_sdk,
    "channel": args.channel,
    "sha256": digest,
}
if args.compile_sdk is not None:
    metadata["compileSdk"] = args.compile_sdk
if args.target_sdk is not None:
    metadata["targetSdk"] = args.target_sdk
if args.v_version:
    metadata["vVersion"] = args.v_version

notes = args.notes.strip() or f"YourStudy Android release {args.version_name}."
print(notes)
print()
print("<!-- math1")
print(json.dumps(metadata, indent=2))
print("-->")
