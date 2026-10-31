#!/usr/bin/env python3
"""Generate a deterministic sitemap and robots.txt for a static Pages site."""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from urllib.parse import quote, urlsplit
from xml.etree import ElementTree as ET

SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9"
ET.register_namespace("", SITEMAP_NS)
NO_INDEX = re.compile(
    r'<meta\b(?=[^>]*\bname\s*=\s*["\']robots["\'])(?=[^>]*\bcontent\s*=\s*["\'][^"\']*\bnoindex\b)[^>]*>',
    re.IGNORECASE,
)
CANONICAL = re.compile(
    r'<link\b(?=[^>]*\brel\s*=\s*["\']canonical["\'])(?=[^>]*\bhref\s*=\s*["\']([^"\']+)["\'])[^>]*>',
    re.IGNORECASE,
)


def route_for(path: Path, site_dir: Path, base_url: str) -> str:
    relative = path.relative_to(site_dir).as_posix()
    if relative == "index.html":
        route = ""
    elif relative.endswith("/index.html"):
        route = relative[: -len("index.html")]
    else:
        route = relative
    return base_url + quote(route, safe="/-_.~")


def canonical_for(path: Path, site_dir: Path, base_url: str) -> str | None:
    text = path.read_text(encoding="utf-8")
    if NO_INDEX.search(text):
        return None
    expected = route_for(path, site_dir, base_url)
    match = CANONICAL.search(text)
    if not match:
        return expected
    if match.group(1) != expected:
        raise ValueError(
            f"{path}: canonical {match.group(1)!r} does not match route {expected!r}"
        )
    return expected


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site-dir", type=Path, required=True)
    parser.add_argument("--base-url", required=True)
    args = parser.parse_args()

    site_dir = args.site_dir.resolve()
    if not site_dir.is_dir() or not (site_dir / "index.html").is_file():
        raise SystemExit(f"site directory must contain index.html: {site_dir}")
    parts = urlsplit(args.base_url)
    if parts.scheme != "https" or not parts.netloc or parts.query or parts.fragment:
        raise SystemExit("--base-url must be a clean HTTPS URL")
    base_url = args.base_url.rstrip("/") + "/"

    pages = []
    for path in sorted(site_dir.rglob("*.html")):
        relative = path.relative_to(site_dir)
        if path.name.casefold() == "404.html" or any(
            part.startswith(".") or part.casefold() in {"draft", "drafts"}
            for part in relative.parts
        ):
            continue
        url = canonical_for(path, site_dir, base_url)
        if url:
            pages.append(url)
    if len(pages) != len(set(pages)):
        raise SystemExit("duplicate canonical URLs found while building sitemap")

    root = ET.Element(f"{{{SITEMAP_NS}}}urlset")
    for url in pages:
        node = ET.SubElement(root, f"{{{SITEMAP_NS}}}url")
        ET.SubElement(node, f"{{{SITEMAP_NS}}}loc").text = url
    ET.indent(root, space="  ")
    xml = ET.tostring(root, encoding="utf-8", xml_declaration=True) + b"\n"
    (site_dir / "sitemap.xml").write_bytes(xml)
    (site_dir / "robots.txt").write_text(
        f"User-agent: *\nAllow: /\nSitemap: {base_url}sitemap.xml\n",
        encoding="utf-8",
    )
    print(f"Generated sitemap.xml with {len(pages)} URL(s) in {site_dir}")


if __name__ == "__main__":
    main()
