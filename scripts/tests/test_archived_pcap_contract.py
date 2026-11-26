"""Check dormant libpcap contracts without capture or platform backends."""

from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which("cc"), "host C compiler required")
class ArchivedStatusContract(unittest.TestCase):
    def compile_and_run(self, program):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "contract"
            subprocess.run(["cc", "-std=c99", "-D_DEFAULT_SOURCE", "-Werror",
                            "-I", str(ROOT / "tcpdump/jni/libpcap"),
                            "-x", "c", "-", "-o", binary],
                           input=program, text=True, check=True)
            subprocess.run([binary], check=True)

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
        self.compile_and_run(program)

    def test_private_allocation_follows_handle_and_is_zero_initialized(self):
        source = (ROOT / "tcpdump/jni/libpcap/pcap.c").read_text()
        allocator = re.search(r"^static pcap_t \*\npcap_alloc_pcap_t\(.*?^\}",
                              source, re.M | re.S)
        self.assertIsNotNone(allocator)
        program = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <string.h>
            #include <errno.h>
            #define HAVE_SNPRINTF 1
            #define HAVE_VSNPRINTF 1
            #ifdef strlcpy
            #undef strlcpy
            #endif
            #include "pcap-int.h"
            #define pcap_strerror strerror
        """ + allocator.group() + """
            int main(void) {
                size_t sizes[] = {0, 1, 7, 257};
                char error[PCAP_ERRBUF_SIZE];
                for (size_t i = 0; i < sizeof(sizes) / sizeof(sizes[0]); ++i) {
                    pcap_t *p = pcap_alloc_pcap_t(error, sizes[i]);
                    if (p == NULL) return 1;
                    if (sizes[i] == 0) {
                        if (p->priv != NULL) return 2;
                    } else {
                        unsigned char *tail = p->priv;
                        if ((size_t)(tail - (unsigned char *)p) != sizeof(*p))
                            return 3;
                        for (size_t j = 0; j < sizes[i]; ++j)
                            if (tail[j] != 0) return 4;
                        memset(tail, 0xa5, sizes[i]);
                        if (tail[0] != 0xa5 || tail[sizes[i] - 1] != 0xa5)
                            return 5;
                    }
                    if (p->fd != -1 || p->selectable_fd != -1) return 6;
                    free(p);
                }
                return 0;
            }
        """
        self.compile_and_run(program)

    def test_datalink_names_round_trip_without_internal_prefix(self):
        source = (ROOT / "tcpdump/jni/libpcap/pcap.c").read_text()
        names = re.search(
            r"^static const u_char charmap\[\].*?"
            r"^pcap_datalink_val_to_name\(.*?^\}", source, re.M | re.S)
        self.assertIsNotNone(names)
        program = """
            #include <ctype.h>
            #include <string.h>
            #include "pcap/pcap.h"
        """ + names.group() + """
            int main(void) {
                for (size_t i = 0; dlt_choices[i].name != NULL; ++i) {
                    const char *name = pcap_datalink_val_to_name(dlt_choices[i].dlt);
                    char lower[128];
                    if (name == NULL || strlen(name) >= sizeof(lower)) return 1;
                    if (strncmp(name, "DLT_", 4) == 0) return 2;
                    if (strcmp(name, dlt_choices[i].name + 4) != 0) return 3;
                    if (pcap_datalink_name_to_val(name) != dlt_choices[i].dlt)
                        return 4;
                    for (size_t j = 0; j <= strlen(name); ++j)
                        lower[j] = (char)tolower((unsigned char)name[j]);
                    if (pcap_datalink_name_to_val(lower) != dlt_choices[i].dlt)
                        return 5;
                }
                if (pcap_datalink_name_to_val("EN10MB") != DLT_EN10MB) return 6;
                if (pcap_datalink_name_to_val("DLT_EN10MB") != -1) return 7;
                if (pcap_datalink_name_to_val("") != -1) return 8;
                if (pcap_datalink_name_to_val("not-a-datalink") != -1) return 9;
                if (pcap_datalink_val_to_name(-1) != NULL) return 10;
                return 0;
            }
        """
        self.compile_and_run(program)
