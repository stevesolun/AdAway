"""Check the dormant compatibility wrapper with an inert local status callback."""

from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ArchivedStatusContract(unittest.TestCase):
    @unittest.skipUnless(shutil.which("cc"), "host C compiler required")
    def test_negative_backend_status_never_reports_success(self):
        source = (ROOT / "tcpdump/jni/libpcap/pcap.c").read_text()
        wrapper = re.search(r"^int\npcap_sendpacket\([^}]+^\}", source, re.M)
        self.assertIsNotNone(wrapper)
        program = """
            #include <stddef.h>
            typedef unsigned char u_char;
            typedef struct handle pcap_t;
            struct handle { int (*inject_op)(pcap_t *, const void *, size_t); };
        """ + wrapper.group() + """
            static int status;
            static int inert(pcap_t *p, const void *b, size_t n) { return status; }
            int main(void) {
                pcap_t h = {inert};
                int given[] = {-3, -1, 0, 4}, want[] = {-1, -1, 0, 0};
                for (int i = 0; i < 4; ++i) {
                    status = given[i];
                    if (pcap_sendpacket(&h, NULL, 0) != want[i]) return i + 1;
                }
                return 0;
            }
        """
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "status"
            subprocess.run(["cc", "-std=c99", "-Werror", "-x", "c", "-", "-o", binary],
                           input=program, text=True, check=True)
            subprocess.run([binary], check=True)
