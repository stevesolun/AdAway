# REL-004 UX Matrix Packet Evidence - 2026-06-28

Scope:
- Story: `REL-004`
- Commit under test: `1a25953daf58d51b56d796e641e34e6cf34bfc65`
- Device: `adaway-api34-16g(AVD) - 14`
- Output directory: `app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d`
- Note: this refresh supersedes the earlier packet generated from source commit
  `e762f2b4f73bcd1e20342572a2285c23d9c3c52b`.

## Command

```bash
pwsh -NoProfile -File scripts/run-ux-matrix.ps1 \
  -AndroidHome "$HOME/.local/android-sdk" \
  -JavaHome /opt/homebrew/opt/openjdk@21 \
  -OutputDir app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d \
  -InstrumentationTimeoutSeconds 360
```

## Result

The UX matrix completed all five variants:

```text
baseline: OK (1 test), 8 screenshots pulled
font-1.3: OK (1 test), 8 screenshots pulled
font-1.6: OK (1 test), 8 screenshots pulled
font-1.3-rtl: OK (1 test), 8 screenshots pulled
font-1.6-rtl: OK (1 test), 8 screenshots pulled
UX matrix review packet=app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d/ux-matrix-review.md
```

Generated artifacts:

```text
Screenshot count: 40
Review packet: app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d/ux-matrix-review.md
Review packet SHA-256: d481b9ab3152760fb917474704131b15c44ae45c4aec615581714e5d9e29eae4
Review packet source commit: 1a25953daf58d51b56d796e641e34e6cf34bfc65
```

## Sign-Off Preflight

Command:

```bash
pwsh -NoProfile -File scripts/verify-ux-signoff.ps1 \
  -ReviewPacket app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d/ux-matrix-review.md \
  -Reviewer Codex-preflight \
  -ReportPath app/build/reports/ux-matrix-2026-06-28-rel004-head-1a25953d/ux-signoff-preflight-report.md
```

Expected result:

```text
Status: failed
Source commit: 1a25953daf58d51b56d796e641e34e6cf34bfc65
Review packet source commit: 1a25953daf58d51b56d796e641e34e6cf34bfc65
Review packet SHA-256: d481b9ab3152760fb917474704131b15c44ae45c4aec615581714e5d9e29eae4
Checked items: 0
Unchecked items: 45
Issues: 1
Issue: Review packet still has unchecked items.
```

This is the intended boundary: the screenshot packet exists and is hashable, but `REL-004` is not
closed until a human reviewer checks the packet and `verify-ux-signoff.ps1` produces a passing
`ux-signoff-report.md`.

## Spot Check

The highest-risk local spot-check viewed:
- `font-1.6-rtl/ux-matrix/discover.png`
- `font-1.6-rtl/ux-matrix/home.png`
- `font-1.6-rtl/ux-matrix/sources.png`
- `font-1.6/ux-matrix/more.png`

No obvious clipping, hidden bottom navigation, or unreachable primary action was found in the
current-head-under-test spot-check. Large-font rows are dense by design and still require human review across
the full packet. This is not human release signoff.
