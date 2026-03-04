#!/usr/bin/env python3
"""
AdAway Filter List Scanner - WhatsApp/Telegram Critical Domain Audit
Scans filter lists for domains that would break WA/TG functionality.
"""

import urllib.request
import urllib.error
import re
import json
import time
import sys

# -----------------------------------------------------------------------
# KNOWN RESULTS (already scanned - do not re-download)
# -----------------------------------------------------------------------
ALREADY_KNOWN = {
    "StevenBlack Social": {
        "url": "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts",
        "blocks": [
            "whatsapp.com", "whatsapp.net", "web.whatsapp.com", "fbcdn.net",
            "graph.facebook.com", "b-graph.facebook.com", "edge-chat.facebook.com",
            "mmg.whatsapp.net", "static.whatsapp.net", "wa.me", "www.whatsapp.com"
        ]
    },
    "BlocklistProject Facebook": {
        "url": "https://blokada.org/mirror/v5/blocklist/facebook/hosts.txt",
        "blocks": [
            "mmg.whatsapp.net", "static.whatsapp.net", "wa.me", "whatsapp.com",
            "sonar.fbdo2-1.fna.whatsapp.net", "sonar.fsin9-1.fna.whatsapp.net",
            "w4.web.whatsapp.com", "w5.web.whatsapp.com", "w6.web.whatsapp.com"
        ]
    },
    "DeveloperDan Facebook": {
        "url": "https://blokada.org/mirror/v5/developerdan/facebook/hosts.txt",
        "blocks": [
            "mmg.whatsapp.net", "static.whatsapp.net", "wa.me", "whatsapp.com",
            "sonar-del.cdn.whatsapp.net", "media-xsp1-3.cdn.whatsapp.net"
        ]
    },
    "Anudeep Facebook": {
        "url": "https://raw.githubusercontent.com/anudeepND/blacklist/master/facebook.txt",
        "blocks": [
            "b-graph.facebook.com", "edge-chat.facebook.com", "facebook.com",
            "fbcdn.net", "graph.facebook.com"
        ]
    },
    "HaGeZi Ultimate": {
        "url": "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/hosts/ultimate.txt",
        "blocks": [
            "b-graph.facebook.com", "graph-fallback.facebook.com", "an.facebook.com",
            "ep9.facebook.com", "graph.facebook.com", "graph.whatsapp.com",
            "graph.whatsapp.net", "privatestats.whatsapp.net", "dit.whatsapp.net",
            "ads.telegram.org", "promote.telegram.org"
        ]
    },
    "HaGeZi Multi PRO": {
        "url": "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/hosts/pro.txt",
        "blocks": [
            "an.facebook.com", "tr.facebook.com", "ads.facebook.com",
            "privatestats.whatsapp.net", "dit.whatsapp.net", "crashlogs.whatsapp.net",
            "ads.telegram.org", "promote.telegram.org"
        ]
    },
    "StevenBlack Unified": {
        "url": "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "blocks": [
            "pixel.facebook.com", "an.facebook.com", "sonar-iad.xx.fbcdn.net"
        ]
    },
    "GoodbyeAds": {
        "url": "https://raw.githubusercontent.com/jerryn70/GoodbyeAds/master/Hosts/GoodbyeAds.txt",
        "blocks": [
            "crashlogs.whatsapp.net", "dit.whatsapp.net", "an.facebook.com",
            "analytics.facebook.com"
        ]
    },
    "EasyPrivacy": {
        "url": "https://v.firebog.net/hosts/Easyprivacy.txt",
        "blocks": ["pixel.facebook.com"]
    },
}

# -----------------------------------------------------------------------
# CRITICAL DOMAINS MAP
# -----------------------------------------------------------------------
CRITICAL_DOMAINS = {
    "whatsapp.com":               "CRITICAL - all WA fails",
    "whatsapp.net":               "CRITICAL - all WA fails",
    "wa.me":                      "HIGH - WA links break",
    "web.whatsapp.com":           "HIGH - WA Web breaks",
    "mmg.whatsapp.net":           "HIGH - photos/videos/calls break",
    "static.whatsapp.net":        "HIGH - WA assets break",
    "graph.facebook.com":         "HIGH - WA login/auth breaks",
    "b-graph.facebook.com":       "HIGH - WA auth fallback breaks",
    "graph-fallback.facebook.com":"MEDIUM - WA auth fallback",
    "fbcdn.net":                  "HIGH - WA media CDN",
    "edge-chat.facebook.com":     "MEDIUM - WA chat connectivity",
    "graph.whatsapp.com":         "MEDIUM - WA features",
    "graph.whatsapp.net":         "MEDIUM - WA features",
    "crashlogs.whatsapp.net":     "LOW - telemetry only",
    "dit.whatsapp.net":           "LOW - telemetry only",
    "privatestats.whatsapp.net":  "LOW - telemetry only",
    "telegram.org":               "CRITICAL - all Telegram fails",
    "telegram.me":                "CRITICAL - TG links break",
    "t.me":                       "CRITICAL - TG links break",
    "core.telegram.org":          "CRITICAL - TG API fails",
    "web.telegram.org":           "HIGH - TG Web breaks",
    "ads.telegram.org":           "NONE - only ads",
    "promote.telegram.org":       "NONE - only ads",
}

# Current AdAway User List allowlist (EXACT match only)
CURRENT_ALLOWLIST = {
    "whatsapp.com", "whatsapp.net", "web.whatsapp.com", "www.whatsapp.com",
    "facebook.com", "www.facebook.com", "fbcdn.net", "graph.facebook.com",
    "b-graph.facebook.com"
}

# Lists to download and scan
LISTS_TO_SCAN = [
    {
        "name": "OISD Full",
        "url": "https://hosts.oisd.nl/",
        "skip": False,
    },
    {
        "name": "HaGeZi Light",
        "url": "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/hosts/light.txt",
        "skip": False,
    },
    {
        "name": "HaGeZi TIF",
        "url": "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/hosts/tif.txt",
        "skip": True,
        "skip_reason": "already known SAFE - 670k domains, SAFE"
    },
    {
        "name": "1Hosts Pro",
        "url": "https://o0.pages.dev/Pro/hosts.txt",
        "skip": True,
        "skip_reason": "known 404"
    },
    {
        "name": "AdAway Official",
        "url": "https://adaway.org/hosts.txt",
        "skip": False,
    },
    {
        "name": "Peter Lowe",
        "url": "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
        "skip": False,
    },
    {
        "name": "URLhaus",
        "url": "https://urlhaus.abuse.ch/downloads/hostfile/",
        "skip": False,
    },
    {
        "name": "Fanboy IsraelList",
        "url": "https://fanboy.co.nz/israelilist/IsraelList.txt",
        "skip": False,
    },
]


def extract_blocked_domains(content):
    """
    Extract blocked domains from filter list content.
    Handles:
    - hosts format:  0.0.0.0 domain  /  127.0.0.1 domain
    - plain domain lines
    - ABP format:    ||domain^
    """
    blocked = set()
    hosts_re = re.compile(
        r'^(?:0\.0\.0\.0|127\.0\.0\.1)\s+([a-zA-Z0-9._\-]+)',
        re.MULTILINE
    )
    abp_re = re.compile(
        r'^\|\|([a-zA-Z0-9._\-]+)\^',
        re.MULTILINE
    )
    plain_domain_re = re.compile(
        r'^([a-zA-Z0-9](?:[a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?'
        r'(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?)+)\s*$',
        re.MULTILINE
    )

    for m in hosts_re.finditer(content):
        d = m.group(1).lower().strip('.')
        if d and d not in ('localhost', 'broadcasthost', 'local'):
            blocked.add(d)

    for m in abp_re.finditer(content):
        d = m.group(1).lower().strip('.')
        if d:
            blocked.add(d)

    # Plain domain only if file doesn't look like hosts format already
    if not hosts_re.search(content):
        for m in plain_domain_re.finditer(content):
            d = m.group(1).lower().strip('.')
            if d:
                blocked.add(d)

    return blocked


def domain_is_blocked(domain, blocked_set):
    """
    Check if a critical domain is blocked.
    Exact match OR the blocked set contains the domain itself
    OR a suffix parent is blocked (e.g. blocking 'whatsapp.net'
    catches 'mmg.whatsapp.net').
    """
    if domain in blocked_set:
        return True, domain
    # Check if any parent suffix is blocked
    parts = domain.split('.')
    for i in range(1, len(parts)):
        parent = '.'.join(parts[i:])
        if parent in blocked_set:
            return True, parent
    return False, None


def download_list(name, url, timeout=25):
    """Download a filter list, return text content or None on failure."""
    print(f"  Downloading: {name} ...", end=' ', flush=True)
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "AdAway/13.1.3"}
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            # Try UTF-8, fallback to latin-1
            try:
                content = raw.decode('utf-8')
            except UnicodeDecodeError:
                content = raw.decode('latin-1')
            size_kb = len(raw) // 1024
            print(f"OK ({size_kb} KB)")
            return content
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}")
        return None
    except urllib.error.URLError as e:
        print(f"FAIL: {e.reason}")
        return None
    except Exception as e:
        print(f"ERROR: {e}")
        return None


def scan_list_for_critical(name, content):
    """Scan a downloaded list for critical domain hits."""
    blocked = extract_blocked_domains(content)
    hits = []
    for critical_domain in CRITICAL_DOMAINS:
        found, via = domain_is_blocked(critical_domain, blocked)
        if found:
            hits.append({
                "domain": critical_domain,
                "via": via,  # exact or parent domain that matched
            })
    return hits


def main():
    print("=" * 70)
    print("AdAway WA/TG Filter List Audit Scanner")
    print("=" * 70)

    new_scan_results = {}

    # Step 1: Download and scan new lists
    print("\n[PHASE 1] Downloading and scanning new lists...")
    for lst in LISTS_TO_SCAN:
        name = lst["name"]
        url = lst["url"]

        if lst.get("skip"):
            reason = lst.get("skip_reason", "skipped")
            print(f"  SKIPPING: {name} ({reason})")
            new_scan_results[name] = {
                "url": url,
                "skipped": True,
                "skip_reason": reason,
                "blocks": []
            }
            continue

        content = download_list(name, url)
        if content is None:
            new_scan_results[name] = {
                "url": url,
                "error": "download_failed",
                "blocks": []
            }
            continue

        hits = scan_list_for_critical(name, content)
        hit_domains = [h["domain"] for h in hits]
        new_scan_results[name] = {
            "url": url,
            "blocks": hit_domains,
            "raw_hits": hits
        }
        if hit_domains:
            print(f"    >>> HITS: {hit_domains}")
        else:
            print(f"    >>> CLEAN (no critical domain hits)")

    # Step 2: Merge all results
    print("\n[PHASE 2] Merging all scan results...")
    all_results = {}
    for name, data in ALREADY_KNOWN.items():
        all_results[name] = {
            "url": data["url"],
            "blocks": data["blocks"],
            "source": "already_known"
        }
    for name, data in new_scan_results.items():
        if not data.get("skipped") and not data.get("error"):
            all_results[name] = {
                "url": data["url"],
                "blocks": data["blocks"],
                "source": "newly_scanned"
            }
        elif data.get("skipped"):
            all_results[name] = {
                "url": data["url"],
                "blocks": [],
                "source": "skipped",
                "skip_reason": data.get("skip_reason", "")
            }
        else:
            all_results[name] = {
                "url": data["url"],
                "blocks": [],
                "source": "error",
                "error": data.get("error", "unknown")
            }

    print(f"  Total lists in merged set: {len(all_results)}")

    # Step 3: Build domain risk map
    print("\n[PHASE 3] Building domain risk map...")

    domain_risk = {}
    for critical_domain, impact in CRITICAL_DOMAINS.items():
        blocking_lists = []
        for list_name, list_data in all_results.items():
            if list_data.get("source") in ("skipped", "error"):
                continue
            # Check if this list blocks the domain (exact or parent suffix)
            blocks_set = set(list_data.get("blocks", []))
            found, via = domain_is_blocked(critical_domain, blocks_set)
            if found:
                blocking_lists.append({
                    "list": list_name,
                    "via": via
                })

        is_protected = critical_domain in CURRENT_ALLOWLIST
        is_unprotected = len(blocking_lists) > 0 and not is_protected

        domain_risk[critical_domain] = {
            "impact": impact,
            "blocked_by_count": len(blocking_lists),
            "blocking_lists": blocking_lists,
            "is_protected": is_protected,
            "is_unprotected": is_unprotected
        }

        status = "PROTECTED" if is_protected else ("UNPROTECTED" if is_unprotected else "NOT_BLOCKED")
        print(f"  {critical_domain:40s} {status:12s} blocked_by={len(blocking_lists)}")

    # Step 4: Find unprotected critical domains
    unprotected = [
        d for d, info in domain_risk.items()
        if info["is_unprotected"]
    ]
    print(f"\n  Unprotected domains: {len(unprotected)}")
    for d in unprotected:
        print(f"    - {d} ({CRITICAL_DOMAINS[d]})")

    # Step 5: High-risk lists (those that block CRITICAL/HIGH impact domains not protected)
    print("\n[PHASE 4] Identifying high-risk lists...")
    list_risk_scores = {}
    impact_weights = {
        "CRITICAL": 10,
        "HIGH": 5,
        "MEDIUM": 2,
        "LOW": 0,
        "NONE": 0
    }

    for list_name, list_data in all_results.items():
        if list_data.get("source") in ("skipped", "error"):
            continue
        score = 0
        critical_hits = []
        for critical_domain, risk_info in domain_risk.items():
            if any(b["list"] == list_name for b in risk_info["blocking_lists"]):
                impact = risk_info["impact"].split(" - ")[0]
                weight = impact_weights.get(impact, 0)
                if not risk_info["is_protected"]:
                    score += weight
                    if weight > 0:
                        critical_hits.append(critical_domain)
        list_risk_scores[list_name] = {
            "score": score,
            "unprotected_hits": critical_hits
        }

    high_risk_lists = sorted(
        [(name, data) for name, data in list_risk_scores.items() if data["score"] > 0],
        key=lambda x: x[1]["score"],
        reverse=True
    )

    print("  High-risk lists (break WA/TG functionality):")
    for name, data in high_risk_lists:
        print(f"    score={data['score']:3d}  {name}: {data['unprotected_hits']}")

    # Step 6: Build recommended wildcard allowlist
    # Find all unprotected domains and their parent "wildcards"
    recommended_allowlist = set()
    wa_suffixes = ["whatsapp.com", "whatsapp.net", "fbcdn.net"]
    tg_suffixes = ["telegram.org", "telegram.me", "t.me"]

    for domain in unprotected:
        recommended_allowlist.add(domain)
        # If a subdomain, suggest the parent wildcard too
        parts = domain.split('.')
        if len(parts) > 2:
            parent = '.'.join(parts[1:])
            recommended_allowlist.add(parent)

    # Always recommend the core wildcard roots
    for d in wa_suffixes + tg_suffixes:
        if d not in CURRENT_ALLOWLIST:
            recommended_allowlist.add(d)
        # Add wildcard notation for subdomains
        recommended_allowlist.add(f"*.{d}")

    # Step 7: Write results
    print("\n[PHASE 5] Writing report files...")

    output_dir = "C:/Steves_Files/Work/Research_and_Papers/AdAway/docs/wa-tg-audit"

    # Build JSON report
    report = {
        "scan_metadata": {
            "date": "2026-03-04",
            "scanner_version": "1.0.0",
            "total_lists_evaluated": len(all_results),
            "lists_successfully_scanned": sum(
                1 for d in all_results.values()
                if d.get("source") in ("already_known", "newly_scanned")
            ),
        },
        "all_lists": all_results,
        "domain_risk_map": domain_risk,
        "unprotected_domains": unprotected,
        "high_risk_lists": [
            {
                "name": name,
                "risk_score": data["score"],
                "breaks_these_domains": data["unprotected_hits"]
            }
            for name, data in high_risk_lists
        ],
        "current_allowlist": sorted(CURRENT_ALLOWLIST),
        "recommended_additions_to_allowlist": sorted(recommended_allowlist),
    }

    json_path = f"{output_dir}/domain-scan-report.json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    print(f"  Wrote: {json_path}")

    # Build Markdown summary
    md_lines = []
    md_lines.append("# AdAway WA/TG Filter List Audit — Domain Scan Summary")
    md_lines.append("")
    md_lines.append(f"**Generated:** 2026-03-04  ")
    md_lines.append(f"**Lists evaluated:** {report['scan_metadata']['total_lists_evaluated']}  ")
    md_lines.append(f"**Lists successfully scanned:** {report['scan_metadata']['lists_successfully_scanned']}  ")
    md_lines.append("")

    md_lines.append("## Root Cause: Exact-Match Allowlist Cannot Protect Subdomains")
    md_lines.append("")
    md_lines.append("AdAway's User List allowlist uses **exact host matching**. Adding `whatsapp.net`")
    md_lines.append("to the allowlist does NOT protect `mmg.whatsapp.net`, `static.whatsapp.net`,")
    md_lines.append("`crashlogs.whatsapp.net`, etc. Each subdomain must be individually listed,")
    md_lines.append("or the app needs wildcard/suffix allowlist support.")
    md_lines.append("")
    md_lines.append("Several active filter lists block specific WhatsApp and Telegram subdomains")
    md_lines.append("that are NOT in the current allowlist, silently breaking functionality.")
    md_lines.append("")

    md_lines.append("## Critical Domain Risk Table")
    md_lines.append("")
    md_lines.append("| Domain | Impact | # Lists Block It | Protected | Status |")
    md_lines.append("|--------|--------|-----------------|-----------|--------|")

    for domain, info in sorted(domain_risk.items(),
                                key=lambda x: (
                                    ["CRITICAL","HIGH","MEDIUM","LOW","NONE"].index(
                                        x[1]["impact"].split(" - ")[0]
                                    ),
                                    x[0]
                                )):
        impact_label = info["impact"].split(" - ")[0]
        count = info["blocked_by_count"]
        protected = "YES" if info["is_protected"] else "NO"
        if count == 0:
            status = "SAFE"
        elif info["is_protected"]:
            status = "PROTECTED"
        else:
            status = "**UNPROTECTED**"
        md_lines.append(f"| `{domain}` | {impact_label} | {count} | {protected} | {status} |")

    md_lines.append("")

    md_lines.append("## Filter Lists That BREAK WA/TG (sorted by severity)")
    md_lines.append("")
    md_lines.append("These lists block at least one unprotected critical domain:")
    md_lines.append("")
    md_lines.append("| List | Risk Score | Unprotected Domains Blocked |")
    md_lines.append("|------|------------|----------------------------|")
    for item in high_risk_lists:
        name = item[0]
        data = item[1]
        domains_str = ", ".join(f"`{d}`" for d in data["unprotected_hits"])
        md_lines.append(f"| {name} | {data['score']} | {domains_str} |")

    md_lines.append("")

    md_lines.append("## Unprotected Domains (require allowlist entries)")
    md_lines.append("")
    md_lines.append("These critical domains are blocked by at least one active list but NOT")
    md_lines.append("protected by the current User List allowlist:")
    md_lines.append("")
    for d in sorted(unprotected):
        info = domain_risk[d]
        blocking_names = [b["list"] for b in info["blocking_lists"]]
        md_lines.append(f"- `{d}` — {info['impact']}")
        md_lines.append(f"  - Blocked by: {', '.join(blocking_names)}")
    md_lines.append("")

    md_lines.append("## Recommended Wildcard Allowlist Entries")
    md_lines.append("")
    md_lines.append("To reliably protect WhatsApp and Telegram from all current and future")
    md_lines.append("filter lists, the following entries should be added to the User List allowlist.")
    md_lines.append("Note: AdAway currently does not support wildcard entries natively —")
    md_lines.append("these are the exact subdomains that need to be listed, plus a note")
    md_lines.append("on the wildcard-support enhancement needed.")
    md_lines.append("")
    md_lines.append("### Immediate (exact matches needed now):")
    md_lines.append("```")
    immediate = sorted(d for d in unprotected if not d.startswith('*'))
    for d in immediate:
        md_lines.append(d)
    md_lines.append("```")
    md_lines.append("")
    md_lines.append("### Long-term (wildcard support needed):")
    md_lines.append("```")
    wildcard_recs = [
        "*.whatsapp.com",
        "*.whatsapp.net",
        "*.fbcdn.net",
        "*.facebook.com",
        "*.telegram.org",
        "*.telegram.me",
    ]
    for d in wildcard_recs:
        md_lines.append(d)
    md_lines.append("```")
    md_lines.append("")

    md_lines.append("## Current Allowlist (for reference)")
    md_lines.append("```")
    for d in sorted(CURRENT_ALLOWLIST):
        md_lines.append(d)
    md_lines.append("```")

    md_path = f"{output_dir}/domain-scan-summary.md"
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(md_lines) + "\n")
    print(f"  Wrote: {md_path}")

    # Step 8: Return structured output for task output.json
    task_output = {
        "task_id": "01KJWYVVEF1BH34T6XHT8694P3",
        "status": "Task 01KJWYVVEF1BH34T6XHT8694P3: COMPLETED",
        "listsScanned": report["scan_metadata"]["lists_successfully_scanned"],
        "criticalHits": [
            {
                "domain": domain,
                "impact": info["impact"],
                "blockedByLists": [b["list"] for b in info["blocking_lists"]],
                "isProtected": info["is_protected"]
            }
            for domain, info in domain_risk.items()
            if info["blocked_by_count"] > 0
        ],
        "unprotectedDomains": unprotected,
        "highRiskLists": [item[0] for item in high_risk_lists],
        "reportFile": json_path,
        "summaryFile": md_path,
    }

    task_output_path = "C:/Steves_Files/Work/Research_and_Papers/AdAway/.a5c/runs/01KJWYQRT9V3N1H1HX913ECY5Q/tasks/01KJWYVVEF1BH34T6XHT8694P3/output.json"
    with open(task_output_path, "w", encoding="utf-8") as f:
        json.dump(task_output, f, indent=2)
    print(f"  Wrote task output: {task_output_path}")

    print("\n" + "=" * 70)
    print("SCAN COMPLETE")
    print(f"  Lists scanned: {report['scan_metadata']['lists_successfully_scanned']}")
    print(f"  Unprotected critical domains: {len(unprotected)}")
    print(f"  High-risk lists: {len(high_risk_lists)}")
    print("=" * 70)

    return task_output


if __name__ == "__main__":
    result = main()
    sys.exit(0)
