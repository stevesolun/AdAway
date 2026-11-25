"""Bounded SQLite checks of the actual Room provenance/delete queries on v32 indexes."""

import json
import re
import sqlite3
import unittest

from test_root_export_sql import DAO, DaoSql


class CheckerAllowSqlTest(unittest.TestCase):
    def setUp(self):
        sql = DaoSql()
        self.runtime = sql.query("isAllowedByActiveRule")
        source = DAO.with_name("HostListItemDao.java").read_text()

        def query(method):
            expr = re.search(r"@Query\(((?:(?!@Query).)*?)\)\s+\S+ " + method + r"\(",
                             source, re.S)[1]
            return sql.expand(expr.replace("HostEntryDao.", ""))

        self.matches = query("getMatchingAllowRules")
        self.remove = query("removeExactUserAllow")
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        schema = DAO.parents[7] / "schemas/org.adaway.db.AppDatabase/32.json"
        for entity in json.loads(schema.read_text())["database"]["entities"]:
            table = entity["tableName"]
            if table in ("hosts_lists", "hosts_meta"):
                for ddl in [entity, *entity["indices"]]:
                    self.db.execute(ddl["createSql"].replace("${TABLE_NAME}", table))
        self.db.execute("INSERT INTO hosts_meta VALUES (0,2)")

    def add(self, host, source, kind=0, generation=2, enabled=1, rule_type=1):
        self.db.execute("INSERT INTO hosts_lists(host,source_id,kind,generation,enabled,type) "
                        "VALUES (?,?,?,?,?,?)", (host, source, kind, generation, enabled, rule_type))

    def check(self, host, expected):
        self.assertCountEqual(expected, self.db.execute(self.matches, {"host": host}).fetchall())
        self.assertEqual(bool(expected), self.db.execute(self.runtime, {"host": host}).fetchone()[0])

    def test_matching_and_exact_removal(self):
        self.add("Child_*%.EXAMPLE", 1, generation=0)
        self.add("*.eXample", 2)
        self.add("EXAMPLE", 3, kind=1)
        self.add("CHILD_a.EXAMPLE", 1, generation=0)
        self.add("ch?ld_%.example", 4)
        self.add("*.example", 5, enabled=0)
        self.add("*.example", 6, generation=1)
        self.add("*.example", 7, generation=3)
        self.add("child_a.example", 8, rule_type=0)
        self.add("child_a.example", 9, rule_type=2)
        both = [(2, "*.eXample", 0), (3, "EXAMPLE", 1)]
        broad = both + [(1, "Child_*%.EXAMPLE", 0), (4, "ch?ld_%.example", 0)]
        self.check("child_a.example", broad + [(1, "CHILD_a.EXAMPLE", 0)])
        self.check("childxa.example", both)
        self.check("badexample", [])
        self.db.execute(self.remove, {"host": "child_a.example"})
        self.check("child_a.example", broad)
        self.check("child_b.example", broad)
        self.assertEqual(2, self.db.execute("SELECT count(*) FROM hosts_lists "
                                            "WHERE type IN (0,2)").fetchone()[0])

    def test_provenance_plan_searches_allow_candidates_not_blocks(self):
        self.add("*.example", 2)
        self.db.executemany("INSERT INTO hosts_lists(host,source_id,kind,generation,enabled,type) "
                            "VALUES (?,2,0,2,1,0)", ((f"block{i}.example",) for i in range(8192)))
        self.db.execute("ANALYZE")
        details = [r[-1] for r in self.db.execute("EXPLAIN QUERY PLAN " + self.matches,
                                                   {"host": "missing.example"})]
        self.assertTrue(any("SEARCH allowed" in d and "type=? AND enabled=?" in d
                            for d in details), details)
        self.assertFalse(any("SCAN allowed" in d for d in details), details)
        self.check("missing.example", [(2, "*.example", 0)])


if __name__ == "__main__":
    unittest.main()
