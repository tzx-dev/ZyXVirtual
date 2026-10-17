#!/usr/bin/env python3
"""Wrap a built LineageOS ARM64 virtual-device ZIP as a verifiable .zyxos package."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import stat
import sys
import zipfile
from pathlib import Path, PurePosixPath

MANIFEST_NAME = "ZYX-MANIFEST.json"
CHUNK = 1024 * 1024


def sha256_stream(stream) -> str:
    digest = hashlib.sha256()
    while True:
        block = stream.read(CHUNK)
        if not block:
            break
        digest.update(block)
    return digest.hexdigest()


def validate_name(name: str) -> None:
    if not name or "\\" in name or name.startswith("/"):
        raise ValueError(f"nome de arquivo ZIP inseguro: {name!r}")
    path = PurePosixPath(name)
    if any(part in ("", ".", "..") for part in name.split("/")) or "\x00" in name:
        raise ValueError(f"caminho ZIP inseguro: {name!r}")


def build_manifest(
    source: Path,
    branch: str,
    product: str,
    build_id: str,
    display_name: str = "ZyX OS",
    provenance: dict | None = None,
) -> dict:
    items = []
    seen = set()
    with zipfile.ZipFile(source, "r") as archive:
        infos = archive.infolist()
        names = {info.filename for info in infos}
        if "META-INF/com/android/metadata" in names and "payload.bin" in names:
            raise ValueError(
                "o arquivo é uma OTA Android para dispositivo físico; "
                "ZyX OS exige o ZIP vm-utm-zip do produto virtual"
            )
        for info in infos:
            if info.is_dir():
                continue
            validate_name(info.filename)
            if info.filename in seen:
                raise ValueError(f"entrada ZIP duplicada: {info.filename}")
            seen.add(info.filename)
            mode = (info.external_attr >> 16) & 0xFFFF
            if stat.S_ISLNK(mode):
                raise ValueError(f"links simbólicos não são permitidos no pacote: {info.filename}")
            with archive.open(info, "r") as entry:
                digest = sha256_stream(entry)
            items.append({"path": info.filename, "size": info.file_size, "sha256": digest})
    if not items:
        raise ValueError("o bundle LineageOS está vazio")
    if MANIFEST_NAME in seen:
        raise ValueError(f"o ZIP de origem já contém {MANIFEST_NAME}")
    if not any(item["path"].lower().endswith("config.plist") for item in items):
        raise ValueError("bundle UTM não contém config.plist; não será declarado pacote ZyX OS")
    if not any(item["path"].lower().endswith((".qcow2", ".img")) for item in items):
        raise ValueError("bundle UTM não contém disco virtual; não será declarado pacote ZyX OS")

    manifest = {
        "schemaVersion": 1,
        "packageId": "org.zyx.virtual.os",
        "displayName": display_name,
        "os": {"base": "LineageOS", "lineageBranch": branch, "androidMajorVersion": 16,
               "buildId": build_id, "gappsIncluded": False},
        "guest": {"product": product, "deviceCodename": "zyx_virtual",
                  "baseVirtualTarget": "virtio_arm64only", "architecture": "aarch64"},
        "runtime": {"requiredBackend": "qemu-system-aarch64", "machine": "virt",
                     "packageType": "utm-qemu-virtual-machine", "minimumMemoryMiB": 2048},
        "files": items,
    }
    if provenance is not None:
        manifest["provenance"] = provenance
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path, help="LineageOS vm-utm-zip output")
    parser.add_argument("--output", required=True, type=Path, help="destination .zyxos package")
    parser.add_argument("--lineage-branch", default="lineage-23.2")
    parser.add_argument("--product", default="lineage_zyx_virtual")
    parser.add_argument("--build-id", default="unknown")
    parser.add_argument("--display-name", default="ZyX OS")
    parser.add_argument("--source-repository")
    parser.add_argument("--source-release")
    parser.add_argument("--source-product")
    parser.add_argument("--source-sha256")
    parser.add_argument("--custom-product-overlay-applied", choices=("true", "false"), default="true")
    args = parser.parse_args()

    source = args.source.resolve()
    output = args.output.resolve()
    if not source.is_file():
        parser.error(f"arquivo de origem não encontrado: {source}")
    if source == output:
        parser.error("a origem e o destino devem ser arquivos diferentes")
    if output.exists():
        parser.error(f"o destino já existe; não será sobrescrito: {output}")
    if args.lineage_branch != "lineage-23.2":
        parser.error("este empacotador está fixado para LineageOS 23.2 / Android 16")
    if args.product != "lineage_zyx_virtual":
        parser.error("somente o produto lineage_zyx_virtual é aceito")

    source_fields = (args.source_repository, args.source_release, args.source_product, args.source_sha256)
    provenance = None
    if any(source_fields):
        if not all(source_fields):
            parser.error("proveniência exige repositório, release, produto e SHA-256")
        if not re.fullmatch(r"[0-9a-f]{64}", args.source_sha256):
            parser.error("source-sha256 deve conter 64 caracteres hexadecimais minúsculos")
        provenance = {
            "repository": args.source_repository,
            "release": args.source_release,
            "sourceProduct": args.source_product,
            "sourceSha256": args.source_sha256,
            "customProductOverlayApplied": args.custom_product_overlay_applied == "true",
        }

    try:
        manifest = build_manifest(
            source, args.lineage_branch, args.product, args.build_id,
            display_name=args.display_name, provenance=provenance,
        )
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, output)
        payload = (json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")
        with zipfile.ZipFile(output, "a", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr(MANIFEST_NAME, payload)
        with output.open("rb") as stream:
            final_hash = sha256_stream(stream)
        output.with_suffix(output.suffix + ".sha256").write_text(
            f"{final_hash}  {output.name}\n", encoding="ascii"
        )
        print(f"Pacote ZyX OS criado: {output}")
        print(f"SHA-256: {final_hash}")
        print(f"Entradas verificadas: {len(manifest['files'])}")
        return 0
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as exc:
        print(f"Falha ao empacotar ZyX OS: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
