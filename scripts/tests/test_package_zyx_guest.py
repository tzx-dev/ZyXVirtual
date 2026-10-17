from __future__ import annotations

import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "package-zyx-guest.py"
SPEC = importlib.util.spec_from_file_location("package_zyx_guest", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
PACKAGE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PACKAGE)


class PackageSourceTests(unittest.TestCase):
    def test_rejects_physical_android_ota(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "phone-ota.zip"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("META-INF/com/android/metadata", "pre-device=laurel_sprout\n")
                archive.writestr("payload.bin", b"CrAU")

            with self.assertRaisesRegex(ValueError, "OTA Android para dispositivo físico"):
                PACKAGE.build_manifest(source, "lineage-23.2", "lineage_zyx_virtual", "test")

    def test_accepts_virtual_utm_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "virtual-utm.zip"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("VirtualMachine/UTM/config.plist", "<plist/>")
                archive.writestr("VirtualMachine/UTM/system.qcow2", b"qcow")

            manifest = PACKAGE.build_manifest(
                source,
                "lineage-23.2",
                "lineage_zyx_virtual",
                "test-build",
                display_name="ZyX OS — base upstream",
                provenance={
                    "sourceProduct": "lineage_virtio_arm64only",
                    "customProductOverlayApplied": False,
                },
            )

            self.assertEqual(manifest["guest"]["product"], "lineage_zyx_virtual")
            self.assertEqual(len(manifest["files"]), 2)
            self.assertEqual(manifest["displayName"], "ZyX OS — base upstream")
            self.assertFalse(manifest["provenance"]["customProductOverlayApplied"])


if __name__ == "__main__":
    unittest.main()
