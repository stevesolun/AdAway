# Historical WA/TG Filter List Scan

The original March 4, 2026 generated output is retained in
[domain-scan-report.json](domain-scan-report.json). It predates the scanner's
exact-host/suffix matching and recommendation fixes and is not current
compatibility guidance. Its wildcard and telemetry allowlist recommendations
are superseded; the historical data has not been rescanned.

Use the [filter rule capability matrix](../filter-rule-capabilities.md) for the
app's supported matching rules. The [scanner](scan_lists.py) owns report
matching and recommendation generation; do not hand-update the saved results.
