"""Offline metadata and PNG checks; no Android tools or optional installs.

Run: python3 -B scripts/tests/test_documentation_assets.py -v
PNG chunks and inflated scanlines are always checked. Full pixel decoding uses
Pillow when already installed, otherwise macOS sips; absence is an explicit skip.
Temporary decoder output is discarded, never used to repair repository assets.
"""

import io
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
import zlib


ROOT = Path(__file__).resolve().parents[2]
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
try:
    from PIL import Image
except ImportError:
    Image = None
SIPS = shutil.which("sips")

# Known server terms from every translated/fallback long description. This is a
# regression guard for the removed claims, not general translation validation.
SERVER_TERMS = (
    "local web server", "Yerli server", "lokal webserver", "lokalen Webserver",
    "servidor web local", "web server locale", "web server lokal",
    "serveur Web local", "lokale webserver", "lokal webbserver",
    "server web local", "lokan retservilon", "helyi webszerver",
    "lokalnego serwera WWW", "lokalnog web poslu\u017eitelja",
    "lok\u00e1ln\u00ed webov\u00fd server", "lok\u00e1lny webov\u00fd server",
    "yerel bir web sunucusu", "m\u00e1y ch\u1ee7 web c\u1ee5c b\u1ed9",
    "\u062e\u0627\u062f\u0645 \u0627\u0644\u0648\u064a\u0628 \u0627\u0644\u0645\u062d\u0644\u064a",
    "\u0648\u0628 \u0633\u0631\u0648\u0631 \u0645\u062d\u0644\u06cc",
    "\u043b\u0430\u043a\u0430\u043b\u044c\u043d\u0430\u0433\u0430 \u0432\u044d\u0431-\u0441\u0435\u0440\u0432\u0435\u0440\u0430",
    "\u043b\u043e\u043a\u0430\u043b\u0435\u043d \u0443\u0435\u0431 \u0441\u044a\u0440\u0432\u044a\u0440",
    "\u043b\u043e\u043a\u0430\u043b\u044c\u043d\u043e\u0433\u043e \u0432\u0435\u0431-\u0441\u0435\u0440\u0432\u0435\u0440\u0430",
    "\u03c4\u03bf\u03c0\u03b9\u03ba\u03cc \u03b4\u03b9\u03b1\u03ba\u03bf\u03bc\u03b9\u03c3\u03c4\u03ae \u03b9\u03c3\u03c4\u03bf\u03cd",
    "\u30ed\u30fc\u30ab\u30eb\u306bWeb\u30b5\u30fc\u30d0\u30fc",
    "\ub85c\uceec \uc6f9 \uc11c\ubc84", "\u672c\u6a5f\u7db2\u9801\u4f3a\u670d\u5668",
    "\u672c\u5730\u7684\u7f51\u7edc\u670d\u52a1\u5668",
    "\u0e40\u0e27\u0e47\u0e1a\u0e40\u0e0b\u0e34\u0e23\u0e4c\u0e1f\u0e40\u0e27\u0e2d\u0e23\u0e4c\u0e20\u0e32\u0e22\u0e43\u0e19",
)


def png_chunks(data):
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("Invalid PNG signature")
    chunks = []
    offset = len(PNG_SIGNATURE)
    while offset < len(data):
        if len(data) - offset < 12:
            raise ValueError("Truncated PNG chunk header")
        length, kind = struct.unpack_from(">I4s", data, offset)
        end = offset + 12 + length
        if end > len(data):
            raise ValueError("Truncated PNG chunk payload")
        payload = data[offset + 8:end - 4]
        crc = struct.unpack_from(">I", data, end - 4)[0]
        if zlib.crc32(kind + payload) != crc:
            raise ValueError("PNG chunk CRC mismatch: " + repr(kind))
        chunks.append((kind, payload))
        offset = end
        if kind == b"IEND":
            if payload or offset != len(data):
                raise ValueError("Nonempty IEND or trailing PNG bytes")
            return chunks
    raise ValueError("Missing PNG IEND")


def validate_png(data):
    chunks = png_chunks(data)
    kinds = [kind for kind, _ in chunks]
    if kinds[0] != b"IHDR" or kinds.count(b"IHDR") != 1 or len(chunks[0][1]) != 13:
        raise ValueError("Invalid PNG IHDR")
    width, height, depth, color, compression, filtering, interlace = struct.unpack(
        ">IIBBBBB", chunks[0][1])
    depths = {0: (1, 2, 4, 8, 16), 2: (8, 16), 3: (1, 2, 4, 8), 4: (8, 16), 6: (8, 16)}
    if (not width or not height or width * height > 25_000_000
            or depth not in depths.get(color, ()) or compression or filtering
            or interlace not in (0, 1)):
        raise ValueError("Invalid or oversized documentation PNG header")
    if b"IDAT" not in kinds or (color == 3 and b"PLTE" not in kinds):
        raise ValueError("Missing PNG image data or palette")
    first, last = kinds.index(b"IDAT"), len(kinds) - 1 - kinds[::-1].index(b"IDAT")
    if any(kind != b"IDAT" for kind in kinds[first:last + 1]):
        raise ValueError("Nonconsecutive PNG IDAT chunks")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    # Adam7 pass dimensions, or the single non-interlaced pass.
    passes = ((0, 0, 8, 8), (4, 0, 8, 8), (0, 4, 4, 8), (2, 0, 4, 4),
              (0, 2, 2, 4), (1, 0, 2, 2), (0, 1, 1, 2)) if interlace else ((0, 0, 1, 1),)
    rows = []
    for x, y, dx, dy in passes:
        columns = max(0, (width - x + dx - 1) // dx)
        lines = max(0, (height - y + dy - 1) // dy)
        if columns and lines:
            rows.extend([1 + (columns * channels * depth + 7) // 8] * lines)
    expected = sum(rows)
    decoder = zlib.decompressobj()
    raw = decoder.decompress(b"".join(payload for kind, payload in chunks if kind == b"IDAT"),
                             expected + 1)
    if len(raw) != expected or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
        raise ValueError("Invalid PNG compressed image length or stream")
    offset = 0
    for row_size in rows:
        if raw[offset] > 4:
            raise ValueError("Invalid PNG scanline filter")
        offset += row_size
    return width, height


def documentation_pngs():
    # Read the actual checked-out asset bytes, including newly added PNGs.
    return sorted(path for folder in ("docs", "metadata")
                  for path in (ROOT / folder).rglob("*")
                  if path.is_file() and path.suffix.lower() == ".png")


def fixture_png(raw=b"\x00\xff\x00\x00", compressed=None):
    def chunk(kind, payload):
        return (struct.pack(">I", len(payload)) + kind + payload
                + struct.pack(">I", zlib.crc32(kind + payload)))
    return (PNG_SIGNATURE + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw) if compressed is None else compressed)
            + chunk(b"IEND", b""))


class DocumentationAssetsTest(unittest.TestCase):
    def test_metadata_omits_disabled_local_server_claims(self):
        descriptions = sorted((ROOT / "metadata").glob("*/full_description.txt"))
        self.assertTrue(descriptions, "No store descriptions found")
        failures = []
        for path in descriptions:
            text = path.read_text(encoding="utf-8").casefold()
            matches = [term for term in SERVER_TERMS if term.casefold() in text]
            if matches:
                failures.append(str(path.relative_to(ROOT)) + ": " + ", ".join(matches))
        self.assertFalse(failures, "Unavailable server claims:\n" + "\n".join(failures))

    def test_metadata_retains_custom_ip_option(self):
        descriptions = sorted((ROOT / "metadata").glob("*/full_description.txt"))
        self.assertTrue(descriptions, "No store descriptions found")
        for path in descriptions:
            with self.subTest(path=path.relative_to(ROOT)):
                final = path.read_text(encoding="utf-8").strip().splitlines()[-1].casefold()
                self.assertTrue("ip" in final or "\u0622\u064a \u0628\u064a" in final,
                                "Preserve the supported custom-IP option")

    def test_documentation_png_structure(self):
        paths = documentation_pngs()
        self.assertTrue(paths, "No documentation PNGs found")
        for path in paths:
            with self.subTest(path=path.relative_to(ROOT)):
                validate_png(path.read_bytes())

    @unittest.skipUnless(Image is not None or SIPS, "No existing Pillow or macOS sips decoder")
    def test_documentation_png_full_decode(self):
        paths = documentation_pngs()
        self.assertTrue(paths, "No documentation PNGs found")
        for path in paths:
            with self.subTest(path=path.relative_to(ROOT)):
                if Image is not None:
                    with Image.open(io.BytesIO(path.read_bytes())) as decoded:
                        self.assertEqual("PNG", decoded.format)
                        decoded.load()
                else:
                    # Lossless temporary TIFF forces sips to decode pixels, not
                    # merely inspect headers. It never overwrites a source image.
                    with tempfile.TemporaryDirectory(prefix="adaway-png-check-") as folder:
                        output = Path(folder) / "decoded.tiff"
                        result = subprocess.run(
                            [SIPS, "-s", "format", "tiff", str(path), "--out", str(output)],
                            capture_output=True, text=True, timeout=30)
                        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                        self.assertTrue(output.is_file(), result.stdout + result.stderr)
                        self.assertGreater(output.stat().st_size, 8)
                        self.assertIn(output.read_bytes()[:4], (b"II*\x00", b"MM\x00*"))

    def test_png_parser_accepts_valid_fixture(self):
        self.assertEqual((1, 1), validate_png(fixture_png()))

    def test_png_parser_rejects_corruption(self):
        valid = fixture_png()
        damaged_crc = bytearray(valid)
        damaged_crc[29] ^= 1
        cases = {
            "utf16_signature": b"\xff\xfe" + valid,
            "truncated_header": PNG_SIGNATURE + b"\x00",
            "truncated_chunk": valid[:-1],
            "crc_damage": bytes(damaged_crc),
            "invalid_zlib": fixture_png(compressed=b"not zlib"),
            "short_scanline": fixture_png(raw=b"\x00\xff"),
            "extra_scanline": fixture_png(raw=b"\x00\xff\x00\x00\x00"),
            "invalid_filter": fixture_png(raw=b"\x05\xff\x00\x00"),
            "missing_iend": valid[:-12],
            "trailing_bytes": valid + b"\x00",
        }
        for name, data in cases.items():
            with self.subTest(case=name):
                with self.assertRaises((ValueError, zlib.error)):
                    validate_png(data)


if __name__ == "__main__":
    unittest.main()
