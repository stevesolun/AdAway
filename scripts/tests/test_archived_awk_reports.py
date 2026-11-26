"""Golden stdout for the four standalone legacy AWK reporters; text input only."""

import os
from pathlib import Path
import subprocess
import unittest


SCRIPTS = Path(__file__).resolve().parents[2] / "tcpdump/jni/tcpdump"


def row(clock, fields):
    return f"{clock} src > dst: {fields}\n"


def report(script, trace, *options):
    result = subprocess.run(
        ["awk", *options, "-f", SCRIPTS / (script + ".awk")],
        input=trace, text=True, capture_output=True, check=True,
        env={**os.environ, "LC_ALL": "C"},
    )
    if result.stderr:
        raise AssertionError(result.stderr)
    return result.stdout


class ArchivedReports(unittest.TestCase):
    def test_shared_first_packet_and_rollover(self):
        trace = (row("23:59:59.50", "S 0:0(0)") +
                 row("23:59:59.75", "P 1024:1536(512)") +
                 row("00:00:00.25", ". ack 2048 win 8") +
                 row("00:00:00.50", "P 1536:2048(512)") +
                 row("00:00:00.25", "P 2048:2560(512)"))
        self.assertEqual(report("atime", trace), "   0.75\t2\n")
        self.assertEqual(report("stime", trace), "   0.25\t1\n   1.00\t1.5\n   0.75\t2\n")

    def test_midnight_origin_and_fractional_kbytes(self):
        trace = (row("00:00:00", ". ack 512") + row("00:00:00", "P 512:1024(512)") +
                 row("00:00:02", ". ack 1024") + row("00:00:01", "P 1024:1536(512)"))
        self.assertEqual(report("atime", trace), "   0.00\t0.5\n   2.00\t1\n")
        self.assertEqual(report("stime", trace), "   0.00\t0.5\n   1.00\t1\n")

    def test_missing_records(self):
        for trace in ("", "\nnot a trace\n", row("12:00:00", ". ack") + row("12:00:00", "P win 7")):
            for script in ("atime", "stime", "packetdat", "send-ack"):
                with self.subTest(script=script, trace=trace):
                    expected = "# 0 chunks.  0 packets sent.  0 acks.\n" if script == "packetdat" else ""
                    self.assertEqual(report(script, trace), expected)

    def test_packetdat_midnight_first_and_last(self):
        trace = (row("00:00:00", "P 1:513(512)") + row("00:00:00", ". ack 513") +
                 row("00:00:00.25", "P 1:513(512)") + row("00:00:00.50", ". ack 513"))
        self.assertEqual(report("packetdat", trace),
                         "# 1 chunks.  2 packets sent.  2 acks.\n"
                         "1\t1\t0.00\t0.25\t0.00\t0.50\t2\t2\n")

    def test_packetdat_sparse_cumulative_and_unacked(self):
        trace = (row("23:59:59.50", "P 101:201(100)") + row("00:00:00", ". ack 201") +
                 row("00:00:00.25", "P 301:401(100)"))
        self.assertEqual(report("packetdat", trace, "-v", "packetsize=100"),
                         "# 4 chunks.  2 packets sent.  1 acks.\n"
                         "1\t0\t-1.00\t-1.00\t0.50\t0.50\t0\t0\n"
                         "2\t101\t0.00\t0.00\t0.50\t0.50\t1\t1\n"
                         "3\t0\t-1.00\t-1.00\t-1.00\t-1.00\t0\t0\n"
                         "4\t301\t0.75\t0.75\t-1.00\t-1.00\t1\t0\n")

    def test_packetdat_ack_beyond_last_send(self):
        ack = row("12:00:01", ". ack 1025")
        self.assertEqual(report("packetdat", ack), "# 0 chunks.  0 packets sent.  1 acks.\n")
        self.assertEqual(report("packetdat", row("12:00:00", "P 1:513(512)") + ack),
                         "# 1 chunks.  1 packets sent.  1 acks.\n"
                         "1\t1\t0.00\t0.00\t1.00\t1.00\t1\t0\n")

    def test_packetdat_retains_chunk_order_and_chunk_one_origin(self):
        trace = (row("12:00:00", "P 513:1025(512)") + row("12:00:02", "P 1:513(512)") +
                 row("12:00:03", ". ack 1025"))
        self.assertEqual(report("packetdat", trace),
                         "# 2 chunks.  2 packets sent.  1 acks.\n"
                         "1\t1\t0.00\t0.00\t1.00\t1.00\t1\t0\n"
                         "2\t513\t-2.00\t-2.00\t1.00\t1.00\t1\t1\n")

    def test_event_order_flags_and_retransmit_age(self):
        trace = (row("00:00:00", "P 1:101(100)") + row("00:00:01", "P 1:101(100)") +
                 row("00:00:02", "P 201:301(100)") + row("00:00:03", ". ack 101 win 9") +
                 row("00:00:04", "P 101:201(100)") + row("00:00:05", "P 301:311(10)") +
                 row("00:00:06", ". ack 101 win 9"))
        self.assertEqual(report("send-ack", trace, "-v", "packetsize=100"),
                         "   0.00\t   0.00\t  send P 1\n"
                         "   1.00\t   1.00\t* send P 1  (1.00) [2]\n"
                         "   2.00\t   1.00\t- send P 201\n"
                         "   3.00\t   1.00\t   ack . 101  win 9  (3.00)\n"
                         "   4.00\t   1.00\t  send P 101\n"
                         "   5.00\t   1.00\t# send P 301 <10>\n"
                         "   6.00\t   1.00\t   ack . 101  (6.00) [2]\n")

    def test_event_deltas_controls_and_unmatched_ack(self):
        trace = ("\n" + row("23:59:59", "S 0:0(0)") + row("23:59:59.50", "P 1:513(512)") +
                 row("23:59:59.75", "F 513:513(0)") + row("00:00:00.25", ". ack 999") +
                 row("00:00:00.50", ". ack 513 win 0"))
        self.assertEqual(report("send-ack", trace),
                         "   0.50\t   0.50\t  send P 1\n"
                         "   1.25\t   0.75\t   ack . 999  (-1.00)\n"
                         "   1.50\t   0.25\t   ack . 513  win 0  (1.00)\n")


if __name__ == "__main__":
    unittest.main()
