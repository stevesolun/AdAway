#!/usr/bin/env python3
"""
Fetch all filter lists from FilterLists.com API and classify by WhatsApp/Telegram risk.
"""

import json
import urllib.request
import urllib.error
import sys
import os
import time

# --- Configuration ---
RISK_TAGS = {"social media", "facebook", "privacy", "tracking", "annoyances", "social", "messenger", "communication"}

RISK_NAME_KEYWORDS = [
    "facebook", "social", "whatsapp", "telegram", "messenger",
    "meta", "instagram", "privacy", "tracker"
]

ALREADY_KNOWN_RISKY = [
    "stevenblack social",
    "blocklistproject facebook",
    "developerdan facebook",
    "anudeep facebook blacklist",
    "haGeZi ultimate",
    "goodbyeads",
]

OUTPUT_CATALOG = "C:/Steves_Files/Work/Research_and_Papers/AdAway/docs/wa-tg-audit/catalog-analysis.json"

HEADERS = {
    "User-Agent": "AdAway-FilterAudit/1.0 (security-research)",
    "Accept": "application/json",
}

def fetch_json(url, retries=3, delay=2):
    """Fetch JSON from a URL with retry logic."""
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=30) as resp:
                raw = resp.read()
                return json.loads(raw)
        except urllib.error.HTTPError as e:
            print(f"  HTTP {e.code} for {url} (attempt {attempt+1}/{retries})", file=sys.stderr)
            if attempt < retries - 1:
                time.sleep(delay)
        except urllib.error.URLError as e:
            print(f"  URL error for {url}: {e.reason} (attempt {attempt+1}/{retries})", file=sys.stderr)
            if attempt < retries - 1:
                time.sleep(delay)
        except Exception as e:
            print(f"  Unexpected error for {url}: {e} (attempt {attempt+1}/{retries})", file=sys.stderr)
            if attempt < retries - 1:
                time.sleep(delay)
    return None


def normalize(s):
    return (s or "").lower().strip()


def compute_score(lst, tag_map):
    """
    Compute risk score for a filter list.
    score=3: matches risk tags OR already-known-risky name
    score=2: name or description contains risk keyword
    score=0: no match
    """
    name_lower = normalize(lst.get("name", ""))
    desc_lower = normalize(lst.get("description", ""))

    # Check already-known-risky (score=3)
    for known in ALREADY_KNOWN_RISKY:
        if known.lower() in name_lower:
            return 3

    # Check risk tags (score=3)
    tag_ids = lst.get("tagIds", [])
    for tid in tag_ids:
        tag_name = normalize(tag_map.get(tid, ""))
        if tag_name in RISK_TAGS:
            return 3

    # Check name/description keywords (score=2)
    for kw in RISK_NAME_KEYWORDS:
        if kw in name_lower or kw in desc_lower:
            return 2

    return 0


def extract_view_url(lst):
    """Extract primary view URL."""
    view_urls = lst.get("viewUrls", [])
    if view_urls:
        # viewUrls may be list of strings or list of objects
        first = view_urls[0]
        if isinstance(first, str):
            return first
        elif isinstance(first, dict):
            return first.get("url", "") or first.get("segmentedUrl", "")
    return ""


def main():
    print("=== FilterLists.com Risk Audit ===")
    print()

    # Step 1: Fetch all lists
    print("Fetching filter lists from FilterLists.com API...")
    all_lists = fetch_json("https://api.filterlists.com/lists")
    if all_lists is None:
        print("ERROR: Failed to fetch filter lists.", file=sys.stderr)
        sys.exit(1)
    print(f"  Retrieved {len(all_lists)} filter lists.")

    # Step 2: Fetch all tags
    print("Fetching tags...")
    all_tags = fetch_json("https://api.filterlists.com/tags")
    if all_tags is None:
        print("WARNING: Failed to fetch tags. Tag-based scoring disabled.", file=sys.stderr)
        all_tags = []
    print(f"  Retrieved {len(all_tags)} tags.")

    # Step 3: Build tag map
    tag_map = {}
    for tag in all_tags:
        tag_id = tag.get("id")
        tag_name = tag.get("name", "")
        if tag_id is not None:
            tag_map[tag_id] = tag_name

    print(f"  Tag map built with {len(tag_map)} entries.")
    print()

    # Step 4: Score each list
    print("Classifying filter lists by WhatsApp/Telegram risk...")
    risky_lists = []
    score_counts = {0: 0, 2: 0, 3: 0}

    for lst in all_lists:
        score = compute_score(lst, tag_map)
        score_counts[score] = score_counts.get(score, 0) + 1

        if score >= 2:
            lst_id = lst.get("id")
            name = lst.get("name", "")
            view_url = extract_view_url(lst)
            home_url = ""
            home_urls = lst.get("homeUrls", [])
            if home_urls:
                first_home = home_urls[0]
                if isinstance(first_home, str):
                    home_url = first_home
                elif isinstance(first_home, dict):
                    home_url = first_home.get("url", "")

            tag_names = [tag_map.get(tid, f"tag:{tid}") for tid in lst.get("tagIds", [])]

            risky_lists.append({
                "id": lst_id,
                "name": name,
                "viewUrl": view_url,
                "homeUrl": home_url,
                "tagNames": tag_names,
                "score": score,
                "description": lst.get("description", ""),
            })

    # Sort by score descending, then name
    risky_lists.sort(key=lambda x: (-x["score"], x["name"].lower()))

    high_risk = [r for r in risky_lists if r["score"] == 3]
    medium_risk = [r for r in risky_lists if r["score"] == 2]

    print(f"  Total lists analyzed: {len(all_lists)}")
    print(f"  High risk (score=3):  {len(high_risk)}")
    print(f"  Medium risk (score=2): {len(medium_risk)}")
    print(f"  Total risky:           {len(risky_lists)}")
    print()

    # Step 5: Write catalog output
    catalog_data = {
        "totalLists": len(all_lists),
        "highRiskCount": len(high_risk),
        "mediumRiskCount": len(medium_risk),
        "riskyListsCount": len(risky_lists),
        "catalogFile": OUTPUT_CATALOG,
        "riskyLists": risky_lists,
    }

    os.makedirs(os.path.dirname(OUTPUT_CATALOG), exist_ok=True)
    with open(OUTPUT_CATALOG, "w", encoding="utf-8") as f:
        json.dump(catalog_data, f, indent=2, ensure_ascii=False)

    print(f"Catalog written to: {OUTPUT_CATALOG}")

    # Print summary of risky lists
    print()
    print("=== HIGH RISK LISTS (score=3) ===")
    for r in high_risk:
        print(f"  [{r['id']}] {r['name']}")
        print(f"       Tags: {', '.join(r['tagNames']) or 'none'}")
        print(f"       URL:  {r['viewUrl'] or r['homeUrl'] or 'N/A'}")

    print()
    print("=== MEDIUM RISK LISTS (score=2) ===")
    for r in medium_risk:
        print(f"  [{r['id']}] {r['name']}")
        print(f"       Tags: {', '.join(r['tagNames']) or 'none'}")

    print()
    print("Done.")

    # Return catalog data for output.json
    return catalog_data


if __name__ == "__main__":
    result = main()
    # Print the result as JSON to stdout for capture
    print("\n=== RESULT JSON ===")
    print(json.dumps(result, indent=2, ensure_ascii=False))
