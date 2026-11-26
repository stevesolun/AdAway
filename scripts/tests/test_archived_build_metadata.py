"""Static archive checks; no Windows tools, compilation, installation or capture."""

import os
from pathlib import Path
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
PCAP = ROOT / "tcpdump/jni/libpcap"
TCP = ROOT / "tcpdump/jni/tcpdump"
PRJ = TCP / "win32/prj"


def make_words(path, variable):
    text = path.read_text().replace("\\\n", " ")
    return re.search(rf"^{variable}\s*=([^\n]*)", text, re.M)[1].split()


def project_inputs(path):
    if path.suffix == ".dsp":
        refs = re.findall(r"^SOURCE=(.+)$", path.read_text(), re.M)
    elif path.suffix == ".vcproj":
        refs = [node.attrib["RelativePath"] for node in ET.parse(path).iter("File")]
    else:
        refs = [str(Path(obj).with_suffix(".c")) for obj in make_words(path, "OBJS")]
    return [Path(os.path.normpath(path.parent / ref.strip('"').replace("\\", "/")))
            for ref in refs]


class ArchiveBuildMetadata(unittest.TestCase):
    def test_project_inventories(self):
        tracked = set(subprocess.check_output(
            ["git", "ls-files", "-z", "--", "tcpdump/jni"], cwd=ROOT
        ).decode().split("\0"))
        tcp_core = set(make_words(TCP / "Makefile.in", "LIBNETDISSECT_SRC"))
        tcp_core.update(make_words(TCP / "Makefile.in", "CSRC"))
        # All three projects define INET6; configure selects these extra sources.
        ipv6 = re.search(r'LOCALSRC="([^"$]*print-babel\.c) \$LOCALSRC"',
                         (TCP / "configure.in").read_text())[1].split()
        tcp_core.update(ipv6)
        for project in [PCAP / "Win32/Prj/libpcap.dsp", PRJ / "WinDump.dsp",
                        PRJ / "WinDump.vcproj", PRJ / "GNUmakefile"]:
            with self.subTest(project=project.name):
                paths = project_inputs(project)
                self.assertEqual(len(set(paths)), len(paths))
                self.assertFalse({p.relative_to(ROOT).as_posix() for p in paths} - tracked)
                if project.name == "libpcap.dsp":
                    expected = set(make_words(PCAP / "Makefile.in", "CSRC"))
                    expected.update(["pcap-win32.c", "fad-win32.c", "grammar.c",
                                     "scanner.c", "bpf/net/bpf_filter.c"])
                    self.assertFalse(expected - {p.relative_to(PCAP).as_posix() for p in paths})
                else:
                    inputs = {p.relative_to(TCP).as_posix() for p in paths}
                    self.assertFalse(tcp_core - inputs)
                    # The projects declare that WinPcap provides these APIs.
                    self.assertFalse(inputs & {"bpf_dump.c", "missing/datalinks.c",
                                               "missing/dlnames.c"})

    def test_release_and_debug_definitions(self):
        project = ET.parse(PRJ / "WinDump.vcproj")
        configs = project.findall("./Configurations/Configuration")
        self.assertEqual({c.get("Name") for c in configs}, {"Release|Win32", "Debug|Win32"})
        for config in configs:
            cc = config.find("Tool[@Name='VCCLCompilerTool']")
            self.assertIn("USE_ETHER_NTOHOST", cc.get("PreprocessorDefinitions").split(";"))
            ld = config.find("Tool[@Name='VCLinkerTool']")
            self.assertIn("ws2_32.lib", ld.get("AdditionalDependencies").split())

    def test_install_graph_dry_run(self):
        commands = subprocess.check_output(["make", "-Bn", "install"], cwd=PRJ, text=True)
        self.assertLess(commands.index("-o windump.exe"), commands.index("cp windump.exe"))

    def test_pcap_config(self):
        template = (PCAP / "pcap-config.in").read_text()
        for libdir, rpath_opt, rpath in [
            ("${exec_prefix}/lib", "-Wl,-rpath,", ["-Wl,-rpath,/example/lib"]),
            ("/usr/lib", "-Wl,-rpath,", []), ("/example/lib", "", [])
        ]:
            values = {"prefix": "/example", "exec_prefix": "${prefix}",
                      "includedir": "${prefix}/include", "libdir": libdir,
                      "V_RPATH_OPT": rpath_opt, "LIBS": "-L/dep -ldep"}
            script = re.sub(r"@(\w+)@", lambda m: values[m[1]], template)
            for static in (False, True):
                for cflags in (False, True):
                    for mode in (None, "--libs", "--additional-libs"):
                        args = (["--static"] if static else []) + (["--cflags"] if cflags else [])
                        if mode:
                            args.append(mode)
                        flags = ["-I/example/include"] if cflags else []
                        if mode == "--libs":
                            flags += ["-L" + libdir.replace("${exec_prefix}", "/example")]
                            flags += ([] if static else rpath) + ["-lpcap"]
                        if static and mode:
                            flags += ["-L/dep", "-ldep"]
                        with self.subTest(libdir=libdir, rpath=rpath, args=args):
                            result = subprocess.check_output(
                                ["sh", "-s", "--", *args], input=script, text=True,
                                env=dict(os.environ, RPATH="leak", show_additional_libs="1"),
                            )
                            self.assertEqual(result.split(), flags)
