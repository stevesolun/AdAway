"""Unprivileged SQLite checks of SQL extracted from HostEntryDao, not Room execution."""

import json
import os
from pathlib import Path
import re
import sqlite3
import unittest


DAO = Path(__file__).resolve().parents[2] / (
    "app/src/main/java/org/adaway/db/dao/HostEntryDao.java"
)


class DaoSql:
    def __init__(self):
        self.source = Path(os.environ.get("ROOT_EXPORT_DAO", DAO)).read_text()
        self.constants = dict(re.findall(
            r"^    String (\w+)\s*=\s*(.*?);", self.source, re.M | re.S
        ))
        self.queries = dict((name, expression) for expression, name in re.findall(
            r"@Query\((.*?)\)\s*\n    [\w<>]+ (\w+)\(", self.source, re.S
        ))

    def expand(self, expression, variables=None):
        variables = variables or {}
        result = []
        # Only Java string literals, identifiers and concatenation are accepted.
        tokens = re.compile(r'\s*("(?:\\.|[^"\\])*"|[A-Za-z_]\w*|\+)')
        position = 0
        while position < len(expression.rstrip()):
            match = tokens.match(expression, position)
            if match is None:
                raise ValueError(expression[position:])
            token = match.group(1)
            position = match.end()
            if token.startswith('"'):
                result.append(json.loads(token))
            elif token in variables:
                result.append(str(variables[token]))
            elif token != "+":
                result.append(self.expand(self.constants[token], variables))
        return "".join(result)

    def query(self, name):
        return self.expand(self.queries[name])

    def method(self, name):
        return re.search(
            r"    private void " + name + r"\(.*?\n    }", self.source, re.S
        ).group()

    def copy_redirects(self, db, user_rules):
        body = self.method("insertRootExportStagedRedirectedRows")
        branches = re.search(
            r"String sourceWhere = userRules\s*\? (.*?)\s*: (.*?);", body, re.S
        )
        variables = {"activeGeneration": 2}
        variables["sourceWhere"] = self.expand(
            branches.group(1 if user_rules else 2), variables
        )
        expression = re.search(r"db.execSQL\((.*?)\);", body, re.S).group(1)
        db.execute(self.expand(expression, variables))

    def dedupe(self, db):
        for expression in re.findall(
            r"db.execSQL\((.*?)\);",
            self.method("dedupeRootExportRowsByPrecedence"), re.S
        ):
            db.execute(self.expand(expression))


class RootExportSqlTest(unittest.TestCase):
    def setUp(self):
        self.sql = DaoSql()
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.executescript("""
            CREATE TABLE hosts_sources(id INTEGER PRIMARY KEY, enabled INTEGER);
            INSERT INTO hosts_sources VALUES (1, 1), (2, 1), (3, 1), (4, 1);
            CREATE TABLE hosts_lists(id INTEGER PRIMARY KEY, host TEXT,
                reverse_host TEXT, kind INTEGER, type INTEGER, redirection TEXT,
                source_id INTEGER, generation INTEGER, enabled INTEGER);
            CREATE TABLE root_host_entries_stage(id INTEGER PRIMARY KEY, host TEXT,
                reverse_host TEXT, type INTEGER, redirection TEXT,
                source_id INTEGER, generation INTEGER);
            CREATE TABLE root_host_entries(id INTEGER PRIMARY KEY, host TEXT,
                reverse_host TEXT, kind INTEGER, type INTEGER, redirection TEXT);
            CREATE TABLE root_export_skip_stage_ids(id INTEGER PRIMARY KEY);
        """)

    def stage(self, row_id, host, source=2, generation=2, address=None):
        entry_type = 0 if address is None else 2
        self.db.execute("INSERT INTO root_host_entries_stage VALUES (?, ?, ?, ?, ?, ?, ?)",
                        (row_id, host, host, entry_type, address, source, generation))
        self.db.execute("INSERT INTO hosts_lists VALUES (?, ?, ?, 0, ?, ?, ?, ?, 1)",
                        (row_id, host, host, entry_type, address, source, generation))

    def assert_stage_readers(self, expected):
        for method in ("getAllForRootHostsFileStageMaterialized",
                       "getRootHostsFileCursorStageMaterialized"):
            cursor = self.db.execute(self.sql.query(method), {"activeGeneration": 2})
            columns = [column[0] for column in cursor.description]
            rows = [dict(zip(columns, row)) for row in cursor]
            self.assertEqual(len(expected), len(rows), method)
            self.assertEqual(expected, {row["host"]: row["redirection"] for row in rows})
        count = self.db.execute(self.sql.query("getRootExportStageBackedEntryCountNow"),
                                {"activeGeneration": 2}).fetchone()[0]
        self.assertEqual(len(expected), count)
        for ipv6 in (False, True):
            method = "getRootHostsFileChunkCursorStageMaterialized" + ("Ipv6" if ipv6 else "")
            self.assert_chunks(method, expected, ipv6)

    def assert_chunks(self, method, expected, ipv6):
        after_id = 0
        lines = []
        total = 0
        # One-row pages expose holes in stage IDs and export-ID allocation mistakes.
        for _ in range(len(expected) + 2):
            chunk, last_id, count = self.db.execute(self.sql.query(method), {
                "activeGeneration": 2, "afterId": after_id, "limit": 1,
                "redirectionIpv4": "0.0.0.0", "redirectionIpv6": "::",
                "lineSeparator": "\n",
            }).fetchone()
            if count == 0:
                self.assertIsNone(chunk)
                break
            self.assertGreater(last_id, after_id)
            after_id = last_id
            total += count
            lines.extend(chunk.splitlines())
        else:
            self.fail("chunk traversal did not terminate")
        expected_lines = []
        for host, address in expected.items():
            expected_lines.append((address or "0.0.0.0") + " " + host)
            if ipv6 and address is None:
                expected_lines.append(":: " + host)
        self.assertEqual(len(expected), total)
        self.assertCountEqual(expected_lines, lines)

    def test_stage_readers_follow_disable_reenable_delete_and_generation(self):
        self.stage(1, "toggle.block.example", source=3)
        self.stage(2, "toggle.redirect.example", source=3, address="9.9.9.9")
        self.stage(3, "deleted.example", source=4)
        self.stage(4, "stale.example", generation=1)
        self.stage(5, "future.example", generation=3)
        self.stage(6, "skipped.example")
        self.stage(100, "active.example")
        self.stage(200, "user.example", source=1, generation=0)
        self.db.execute("INSERT INTO root_export_skip_stage_ids VALUES (6)")
        self.db.execute("DELETE FROM hosts_sources WHERE id = 4")
        self.db.execute("UPDATE hosts_sources SET enabled = 0 WHERE id = 3")
        expected = {"active.example": None, "user.example": None}
        self.assert_stage_readers(expected)
        self.db.execute("UPDATE hosts_sources SET enabled = 1 WHERE id = 3")
        self.assert_stage_readers({**expected, "toggle.block.example": None,
                                   "toggle.redirect.example": "9.9.9.9"})
        self.db.execute("DELETE FROM hosts_sources WHERE id = 3")
        self.assert_stage_readers(expected)

    def test_copied_redirects_match_runtime_in_both_import_orders(self):
        for reverse_order in (False, True):
            with self.subTest(reverse_order=reverse_order):
                self.db.executescript("DELETE FROM root_host_entries_stage;"
                                     "DELETE FROM hosts_lists; DELETE FROM root_host_entries;")
                candidates = [
                    ("priority.example", 3, 2, "1.1.1.1"),
                    ("priority.example", 2, 2, "9.9.9.9"),
                    ("same-source.example", 2, 2, "9.9.9.9"),
                    ("same-source.example", 2, 2, "1.1.1.1"),
                    ("same-source.example", 2, 2, "1.1.1.1"),
                    ("user.example", 2, 2, "1.1.1.1"),
                    ("user.example", 1, 0, "8.8.8.8"),
                    ("priority.example", 2, 1, "0.0.0.1"),
                    ("disabled.example", 4, 2, "2.2.2.2"),
                ]
                if reverse_order:
                    candidates.reverse()
                for index, (host, source, generation, address) in enumerate(candidates):
                    self.stage(index + 10, host, source, generation, address)
                self.db.execute("UPDATE hosts_sources SET enabled = 0 WHERE id = 4")
                self.db.execute("UPDATE hosts_lists SET enabled = 0 WHERE source_id = 4")
                self.db.execute("INSERT INTO root_host_entries VALUES "
                                "(500, 'priority.example', '', 0, 0, NULL), "
                                "(600, 'blocked.example', '', 0, 0, NULL)")
                self.sql.copy_redirects(self.db, False)
                self.sql.copy_redirects(self.db, True)
                self.sql.dedupe(self.db)
                expected = {"blocked.example": None}
                for host in ("priority.example", "same-source.example", "user.example"):
                    cursor = self.db.execute(self.sql.query("getActiveExactRedirectEntry"),
                                             {"host": host, "activeGeneration": 2})
                    row = dict(zip([column[0] for column in cursor.description], cursor.fetchone()))
                    expected[host] = row["redirection"]
                actual = self.db.execute("SELECT host, redirection FROM root_host_entries").fetchall()
                self.assertEqual(len(expected), len(actual))
                self.assertEqual(expected, dict(actual))
                for ipv6 in (False, True):
                    method = "getRootHostsFileChunkCursorMaterialized" + ("Ipv6" if ipv6 else "")
                    self.assert_chunks(method, expected, ipv6)

    def test_stage_chunk_plans_keep_keyset_and_source_primary_key_lookups(self):
        for suffix in ("", "Ipv6"):
            query = self.sql.query("getRootHostsFileChunkCursorStageMaterialized" + suffix)
            plan = "\n".join(row[3] for row in self.db.execute("EXPLAIN QUERY PLAN " + query, {
                "activeGeneration": 2, "afterId": 10, "limit": 1000,
                "redirectionIpv4": "0.0.0.0", "redirectionIpv6": "::", "lineSeparator": "\n",
            }))
            self.assertRegex(plan, r"SEARCH entry USING INTEGER PRIMARY KEY \(rowid>\?\)")
            self.assertRegex(plan, r"SEARCH source USING INTEGER PRIMARY KEY \(rowid=\?\)")
            self.assertNotIn("USE TEMP B-TREE", plan)


if __name__ == "__main__":
    unittest.main()
