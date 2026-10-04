#!/usr/bin/env python3
"""Builds release-notes.md for a tag from CHANGELOG.md plus the SHA-256 checksums.

usage: release-notes.py <tag> <checksums-file> [--prerelease]

Section lookup (first hit wins):
  1. `## [x.y.z]` matching the tag's base version (v1.1.0-beta.1 -> 1.1.0)
  2. `## [Unreleased]`
  3. the top-most `## ` section
so a release never fails just because the changelog heading is missing.
"""
import re
import sys
from pathlib import Path

tag, checksums_file = sys.argv[1], sys.argv[2]
prerelease = "--prerelease" in sys.argv[3:]
base_version = tag.lstrip("v").split("-", 1)[0]

lines = Path("CHANGELOG.md").read_text(encoding="utf-8").splitlines()


def section_for(pattern: str) -> str:
    regex = re.compile(pattern)
    section, inside = [], False
    for line in lines:
        if line.startswith("## "):
            if inside:
                break
            inside = bool(regex.search(line))
        if inside:
            section.append(line)
    return "\n".join(section).strip()


notes = (
    section_for(re.escape(f"[{base_version}]"))
    or section_for(r"\[Unreleased\]")
    or section_for(r"^## ")
)
if not notes:
    notes = f"## {tag}\n\nSee the commit history for details."

parts = [notes, "---"]
if prerelease:
    parts.append(
        "⚠️ **This is a pre-release build** — it may be unstable and is not recommended for general use."
    )
parts.append("### SHA-256 checksums")
parts.append("```text\n" + Path(checksums_file).read_text(encoding="utf-8").strip() + "\n```")

Path("release-notes.md").write_text("\n\n".join(parts) + "\n", encoding="utf-8")
