# REL-004 UX Matrix Packet Evidence - 2026-06-28

Scope:
- Story: `REL-004`
- Commit under test: `e762f2b4 docs: record cto convergence audit`
- Device: `adaway-api34-16g(AVD) - 14`
- Output directory: `app/build/reports/ux-matrix-2026-06-28-rel004-current-head`
- Note: this refresh supersedes the earlier packet generated from source commit
  `6125937c97978346a7ea4d67508f35ab04075c88`.

## Command

```bash
pwsh -NoProfile -File scripts/run-ux-matrix.ps1 \
  -AndroidHome "$HOME/.local/android-sdk" \
  -JavaHome /opt/homebrew/opt/openjdk@21 \
  -OutputDir app/build/reports/ux-matrix-2026-06-28-rel004-current-head \
  -InstrumentationTimeoutSeconds 420
```

## Result

The UX matrix completed all five variants:

```text
baseline: OK (1 test), 8 screenshots pulled
font-1.3: OK (1 test), 8 screenshots pulled
font-1.6: OK (1 test), 8 screenshots pulled
font-1.3-rtl: OK (1 test), 8 screenshots pulled
font-1.6-rtl: OK (1 test), 8 screenshots pulled
UX matrix review packet=app/build/reports/ux-matrix-2026-06-28-rel004-current-head/ux-matrix-review.md
```

Generated artifacts:

```text
Screenshot count: 40
Review packet: app/build/reports/ux-matrix-2026-06-28-rel004-current-head/ux-matrix-review.md
Review packet SHA-256: 0fb50e3a0781ca455908612fc0f9914d2c839ed28a9e481267c05d51e633f2bf
Review packet source commit: e762f2b4f73bcd1e20342572a2285c23d9c3c52b
```

## Sign-Off Preflight

Command:

```bash
pwsh -NoProfile -File scripts/verify-ux-signoff.ps1 \
  -ReviewPacket app/build/reports/ux-matrix-2026-06-28-rel004-current-head/ux-matrix-review.md \
  -Reviewer Codex-preflight \
  -ReportPath app/build/reports/ux-matrix-2026-06-28-rel004-current-head/ux-signoff-preflight-report.md
```

Expected result:

```text
Status: failed
Source commit: e762f2b4f73bcd1e20342572a2285c23d9c3c52b
Review packet source commit: e762f2b4f73bcd1e20342572a2285c23d9c3c52b
Review packet SHA-256: 0fb50e3a0781ca455908612fc0f9914d2c839ed28a9e481267c05d51e633f2bf
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
current-head spot-check. Large-font rows are dense by design and still require human review across
the full packet. This is not human release signoff.
