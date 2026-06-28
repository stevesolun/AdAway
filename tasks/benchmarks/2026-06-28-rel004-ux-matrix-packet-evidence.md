# REL-004 UX Matrix Packet Evidence - 2026-06-28

Scope:
- Story: `REL-004`
- Commit under test: `6125937c docs: tighten release gate handoff`
- Device: `adaway-api34-16g(AVD) - 14`
- Output directory: `app/build/reports/ux-matrix-2026-06-28-rel004`

## Command

```bash
pwsh -NoProfile -File scripts/run-ux-matrix.ps1 \
  -AndroidHome "$HOME/.local/android-sdk" \
  -JavaHome "$HOME/.local/jdks/temurin-21/Contents/Home" \
  -OutputDir app/build/reports/ux-matrix-2026-06-28-rel004 \
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
UX matrix review packet=app/build/reports/ux-matrix-2026-06-28-rel004/ux-matrix-review.md
```

Generated artifacts:

```text
Screenshot count: 40
Review packet: app/build/reports/ux-matrix-2026-06-28-rel004/ux-matrix-review.md
Review packet SHA-256: a28fa2dc5740c603ae37fc358746a31773cb9d8384927871948de6abf311db19
Review packet source commit: 6125937c97978346a7ea4d67508f35ab04075c88
```

## Sign-Off Preflight

Command:

```bash
pwsh -NoProfile -File scripts/verify-ux-signoff.ps1 \
  -ReviewPacket app/build/reports/ux-matrix-2026-06-28-rel004/ux-matrix-review.md \
  -Reviewer "Codex preflight - human review pending" \
  -ReportPath app/build/reports/ux-matrix-2026-06-28-rel004/ux-signoff-preflight-report.md
```

Expected result:

```text
Status: failed
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

No obvious clipping, hidden bottom navigation, or unreachable primary action was found in that
spot-check. This is not human release signoff.
