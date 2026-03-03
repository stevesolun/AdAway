package org.adaway.model.source;

import org.junit.Test;

import java.util.regex.Matcher;

import static org.adaway.model.source.SourceLoader.UNBOUND_LOCAL_ZONE;
import static org.adaway.model.source.SourceLoader.UNBOUND_LOCAL_DATA;
import static org.adaway.model.source.SourceLoader.RPZ_CNAME_DOT;
import static org.adaway.model.source.SourceLoader.SURGE_DOMAIN_RULE;
import static org.adaway.model.source.SourceLoader.BIND_ZONE_STMT;
import static org.junit.Assert.*;

/**
 * Tests for the 4 new parser patterns added to SourceLoader:
 *  - UNBOUND_LOCAL_ZONE  (Unbound DNS config format)
 *  - UNBOUND_LOCAL_DATA  (Unbound DNS data record format)
 *  - RPZ_CNAME_DOT       (BIND Response Policy Zone format)
 *  - SURGE_DOMAIN_RULE   (Surge / Quantumult / Clash domain rule format)
 *  - BIND_ZONE_STMT      (BIND zone statement format)
 *
 * Also tests ABP/uBO $options rule handling in extractHostnameFromNonHostsSyntax.
 */
public class SourceLoaderParserPatternsTest {

    // -----------------------------------------------------------------------
    // UNBOUND_LOCAL_ZONE tests
    // -----------------------------------------------------------------------

    @Test
    public void unboundLocalZone_matchesAlwaysRefuse() {
        String line = "local-zone: \"ads.example.com\" always_refuse";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertTrue("UNBOUND_LOCAL_ZONE should match always_refuse line", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void unboundLocalZone_matchesAlwaysNxdomain() {
        String line = "local-zone: \"tracker.evil.net\" always_nxdomain";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertTrue("UNBOUND_LOCAL_ZONE should match always_nxdomain line", m.matches());
        assertEquals("tracker.evil.net", m.group(1));
    }

    @Test
    public void unboundLocalZone_matchesWithLeadingWhitespace() {
        String line = "  local-zone: \"ads.example.com\" static";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertTrue("UNBOUND_LOCAL_ZONE should match with leading whitespace", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void unboundLocalZone_noMatchOnPlainDomain() {
        String line = "ads.example.com";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertFalse("UNBOUND_LOCAL_ZONE should not match plain domain", m.matches());
    }

    @Test
    public void unboundLocalZone_noMatchOnLocalData() {
        String line = "local-data: \"ads.example.com A 0.0.0.0\"";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertFalse("UNBOUND_LOCAL_ZONE should not match local-data lines", m.matches());
    }

    // -----------------------------------------------------------------------
    // UNBOUND_LOCAL_DATA tests
    // -----------------------------------------------------------------------

    @Test
    public void unboundLocalData_matchesARecord() {
        String line = "local-data: \"tracker.com A 0.0.0.0\"";
        Matcher m = UNBOUND_LOCAL_DATA.matcher(line);
        assertTrue("UNBOUND_LOCAL_DATA should match A record line", m.matches());
        assertEquals("tracker.com", m.group(1));
    }

    @Test
    public void unboundLocalData_matchesWithLeadingWhitespace() {
        String line = "  local-data: \"ads.evil.org A 127.0.0.1\"";
        Matcher m = UNBOUND_LOCAL_DATA.matcher(line);
        assertTrue("UNBOUND_LOCAL_DATA should match with leading whitespace", m.matches());
        assertEquals("ads.evil.org", m.group(1));
    }

    @Test
    public void unboundLocalData_noMatchOnLocalZone() {
        String line = "local-zone: \"ads.example.com\" always_refuse";
        Matcher m = UNBOUND_LOCAL_DATA.matcher(line);
        assertFalse("UNBOUND_LOCAL_DATA should not match local-zone lines", m.matches());
    }

    // -----------------------------------------------------------------------
    // RPZ_CNAME_DOT tests
    // -----------------------------------------------------------------------

    @Test
    public void rpzCnameDot_matchesSimpleCname() {
        String line = "ads.example.com CNAME .";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertTrue("RPZ_CNAME_DOT should match simple CNAME . line", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void rpzCnameDot_matchesWithTtl() {
        String line = "tracking.site.com 60 CNAME .";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertTrue("RPZ_CNAME_DOT should match line with TTL", m.matches());
        assertEquals("tracking.site.com", m.group(1));
    }

    @Test
    public void rpzCnameDot_matchesWithInClass() {
        String line = "malware.host.ru 60 IN CNAME .";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertTrue("RPZ_CNAME_DOT should match line with TTL and IN class", m.matches());
        assertEquals("malware.host.ru", m.group(1));
    }

    @Test
    public void rpzCnameDot_matchesWithInClassNoTtl() {
        String line = "bad.domain.com IN CNAME .";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertTrue("RPZ_CNAME_DOT should match line with IN class and no TTL", m.matches());
        assertEquals("bad.domain.com", m.group(1));
    }

    @Test
    public void rpzCnameDot_noMatchOnSoaRecord() {
        // @ SOA rpz.urlhaus.abuse.ch. ... should not match — starts with @
        String line = "@ SOA rpz.urlhaus.abuse.ch. abuse.ch. 2020060501 1 1 604800 300";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertFalse("RPZ_CNAME_DOT should not match SOA records (@ start)", m.matches());
    }

    @Test
    public void rpzCnameDot_noMatchOnNsRecord() {
        // NS record should not match — has NS not CNAME
        String line = "rpz.urlhaus.abuse.ch. NS localhost.";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertFalse("RPZ_CNAME_DOT should not match NS records", m.matches());
    }

    @Test
    public void rpzCnameDot_noMatchOnCnameToNonDot() {
        // CNAME to a real hostname (not ".") should not match
        String line = "ads.example.com CNAME real.redirect.com.";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertFalse("RPZ_CNAME_DOT should not match CNAME to non-dot target", m.matches());
    }

    // -----------------------------------------------------------------------
    // SURGE_DOMAIN_RULE tests
    // -----------------------------------------------------------------------

    @Test
    public void surgeDomainRule_matchesDomainSuffix() {
        String line = "DOMAIN-SUFFIX,ads.example.com";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertTrue("SURGE_DOMAIN_RULE should match DOMAIN-SUFFIX rule", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void surgeDomainRule_matchesDomainFull() {
        String line = "DOMAIN-FULL,tracking.example.com";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertTrue("SURGE_DOMAIN_RULE should match DOMAIN-FULL rule", m.matches());
        assertEquals("tracking.example.com", m.group(1));
    }

    @Test
    public void surgeDomainRule_matchesDomainNoSuffix() {
        // bare DOMAIN, rule
        String line = "DOMAIN,exact.domain.com";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertTrue("SURGE_DOMAIN_RULE should match bare DOMAIN rule", m.matches());
        assertEquals("exact.domain.com", m.group(1));
    }

    @Test
    public void surgeDomainRule_noMatchOnDomainKeyword() {
        // DOMAIN-KEYWORD is a substring match, not a hostname — must be excluded
        String line = "DOMAIN-KEYWORD,ad";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertFalse("SURGE_DOMAIN_RULE should not match DOMAIN-KEYWORD", m.matches());
    }

    @Test
    public void surgeDomainRule_noMatchOnIpCidr() {
        String line = "IP-CIDR,192.168.0.0/24,REJECT";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertFalse("SURGE_DOMAIN_RULE should not match IP-CIDR rules", m.matches());
    }

    @Test
    public void surgeDomainRule_matchesWithTrailingWhitespace() {
        String line = "DOMAIN-SUFFIX,0z5jn.cn  ";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertTrue("SURGE_DOMAIN_RULE should match with trailing whitespace", m.matches());
        assertEquals("0z5jn.cn", m.group(1));
    }

    // -----------------------------------------------------------------------
    // BIND_ZONE_STMT tests
    // -----------------------------------------------------------------------

    @Test
    public void bindZoneStmt_matchesTypeMaster() {
        String line = "zone \"ads.example.com\" { type master; notify no; file \"null.zone.file\"; };";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertTrue("BIND_ZONE_STMT should match zone statement", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void bindZoneStmt_matchesWithLeadingWhitespace() {
        String line = "  zone \"tracker.evil.net\" { type master; file \"/etc/bind/block.zone\"; };";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertTrue("BIND_ZONE_STMT should match with leading whitespace", m.matches());
        assertEquals("tracker.evil.net", m.group(1));
    }

    @Test
    public void bindZoneStmt_matchesOpenBraceOnly() {
        // Minimal valid form — just zone name and opening brace
        String line = "zone \"ads.tracker.net\" {";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertTrue("BIND_ZONE_STMT should match minimal form with just opening brace", m.matches());
        assertEquals("ads.tracker.net", m.group(1));
    }

    @Test
    public void bindZoneStmt_noMatchOnRootZone() {
        // zone "." is the DNS root zone — sanitizeHostname will reject "." as invalid hostname
        // (this test validates the pattern matches, but the caller must skip it)
        String line = "zone \".\" { type hint; file \"named.ca\"; };";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertTrue("BIND_ZONE_STMT pattern matches root zone (caller must skip '.')", m.matches());
        assertEquals(".", m.group(1));
    }

    @Test
    public void bindZoneStmt_noMatchOnPlainDomain() {
        String line = "ads.example.com";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertFalse("BIND_ZONE_STMT should not match plain domain", m.matches());
    }

    // -----------------------------------------------------------------------
    // ABP/uBO $options rules — must NOT yield a hostname (content-filter, not DNS-blockable)
    // -----------------------------------------------------------------------

    @Test
    public void abpRule_thirdPartyOption_notExtracted() {
        // ||google.com^$third-party = "block 3rd-party requests to google.com"
        // At DNS level this causes false positive: google.com itself is blocked
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||google.com^$third-party"));
    }

    @Test
    public void abpRule_scriptOption_notExtracted() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^$script"));
    }

    @Test
    public void abpRule_multipleOptions_notExtracted() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^$script,image,third-party"));
    }

    @Test
    public void abpRule_dollarNoCaretWithOptions_notExtracted() {
        // ||example.com$third-party (no ^ separator) — still has options
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com$third-party"));
    }

    @Test
    public void abpRule_noOptions_extracted() {
        // ||ads.example.com^ with no $options IS a full domain block
        assertEquals("ads.example.com", SourceLoader.extractHostnameFromNonHostsSyntax("||ads.example.com^"));
    }

    @Test
    public void abpRule_noCaretNoOptions_extracted() {
        // ||tracker.net (bare domain, no ^ and no $) — still a domain block
        assertEquals("tracker.net", SourceLoader.extractHostnameFromNonHostsSyntax("||tracker.net"));
    }
}
