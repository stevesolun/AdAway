# Filter Rule Capability Matrix

AdAway is a DNS/root-hosts blocker. It should not claim full browser filter
compatibility unless a rule can be represented safely at DNS resolution time.

| Rule family | Current treatment | Market-safe claim |
|-------------|-------------------|-------------------|
| Hosts file entries | Supported as exact hosts | Fully supported |
| Plain domain lists | Supported as exact hosts | Fully supported |
| dnsmasq `address=/domain/0.0.0.0`, `address=/domain/::`, `address=/domain/#` | Stored as suffix rules | VPN/runtime suffix matching; root hosts output includes only the suffix base domain |
| dnsmasq `local=/domain/` | Stored as suffix rules | VPN/runtime suffix matching; root hosts output includes only the suffix base domain |
| dnsmasq `server=/domain/upstream` | Skipped | Unsupported upstream routing rule |
| Unbound `local-zone` / `local-data` | Extracted as exact hosts | Domain extraction only |
| RPZ `CNAME .` | Extracted as exact hosts | Domain extraction only |
| Surge/Clash `DOMAIN` / `DOMAIN-FULL` with no action or `REJECT` action | Extracted as exact hosts | Domain extraction only; non-block actions such as `DIRECT` are skipped |
| Surge/Clash `DOMAIN-SUFFIX` with no action or `REJECT` action | Stored as suffix rules | VPN/runtime suffix matching; non-block actions such as `DIRECT` are skipped; root hosts output includes only the suffix base domain |
| ABP/uBO `||domain^` | Stored as suffix rules when no path/options are present | VPN/runtime suffix matching; browser-only options still skipped; root output includes only the suffix base domain |
| ABP/uBO/AdGuard exceptions `@@` | Skipped | Unsupported exception semantics |
| ABP/uBO/AdGuard cosmetics/scriptlets | Skipped | Browser-only, unsupported |
| ABP/uBO path or `$options` rules | Skipped | Browser-only/context-only, unsupported |
| Generic BIND `zone` statements | Skipped | Unsupported unless explicit RPZ block rule |
| Wildcards | Supported only for allow-list matching | Not a general block-list feature |
| Redirect rules | Supported from hosts-style IP mapping when redirect is enabled | DNS redirect support |

Suffix rules are matched on DNS label boundaries only: `example.com` matches
`example.com` and `ads.example.com`, but not `badexample.com`. VPN mode can use
that suffix runtime truth. Root mode cannot express suffix semantics in a
system hosts file, so root hosts output materializes only the base domain
(`example.com`) and must not claim that subdomains are covered by the root
hosts file.

FilterLists.com should be presented as a discovery directory, not as a blanket
recommendation engine. Bulk subscription must be gated by compatibility,
selected URL quality, parse yield, skipped-rule count, source freshness, and
source health.
