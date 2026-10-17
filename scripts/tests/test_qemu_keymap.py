import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from prepare_qemu_runtime import copy_qemu_keymap  # noqa: E402


class QemuKeymapTests(unittest.TestCase):
    def test_copies_pinned_en_us_keymap_into_qemu_data_directory(self):
        source = ROOT / "scripts/assets/qemu-keymaps/en-us"
        expected = "52cf4c523e686078699ed58761c7e1443080305237a5f67a3716832570c5c8e5"
        with tempfile.TemporaryDirectory() as tmp:
            target = copy_qemu_keymap(source, expected, Path(tmp))
            self.assertEqual("en-us", target.name)
            self.assertEqual("keymaps", target.parent.name)
            self.assertEqual(source.read_bytes(), target.read_bytes())
            self.assertEqual(expected, hashlib.sha256(target.read_bytes()).hexdigest())

    def test_rejects_keymap_with_unexpected_hash(self):
        source = ROOT / "scripts/assets/qemu-keymaps/en-us"
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, "SHA-256 fixado"):
                copy_qemu_keymap(source, "0" * 64, Path(tmp))
            self.assertFalse((Path(tmp) / "share/qemu/keymaps/en-us").exists())


if __name__ == "__main__":
    unittest.main()
