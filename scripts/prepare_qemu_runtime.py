#!/usr/bin/env python3
"""Prepare the pinned Android/Termux QEMU runtime as verified app assets."""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path, PurePosixPath
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = ROOT / "scripts/qemu-termux-runtime.lock.json"
WORK_DIR = ROOT / "build/qemu-termux-runtime"
OUTPUT_DIR = ROOT / "app/build/generated/zyx-qemu-runtime/assets/zyx-qemu"
TERMUX_PREFIX = Path("data/data/com.termux/files/usr")
SYSTEM_LIBRARIES = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so"}
CHUNK_SIZE = 1024 * 1024


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(CHUNK_SIZE), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_repo_path(value: str) -> PurePosixPath:
    path = PurePosixPath(value)
    if path.is_absolute() or not path.parts or any(part in ("", ".", "..") for part in path.parts):
        raise ValueError(f"Caminho inseguro no lock Termux: {value!r}")
    return path


def download_package(package: dict, repository: str, cache_dir: Path) -> Path:
    filename = safe_repo_path(package["filename"])
    target = cache_dir / f"{package['name']}_{package['version']}.deb"
    expected_sha = package["sha256"].lower()
    expected_size = int(package["size"])
    if target.is_file() and target.stat().st_size == expected_size and sha256_file(target) == expected_sha:
        return target
    target.unlink(missing_ok=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".part")
    digest = hashlib.sha256()
    received = 0
    request = Request(repository.rstrip("/") + "/" + str(filename), headers={"User-Agent": "ZyXVirtual-runtime-builder/1"})
    try:
        with urlopen(request, timeout=90) as response, temporary.open("wb") as out:
            while True:
                block = response.read(CHUNK_SIZE)
                if not block:
                    break
                received += len(block)
                if received > expected_size:
                    raise ValueError(f"Pacote maior que o declarado no lock: {package['name']}")
                digest.update(block)
                out.write(block)
        if received != expected_size or digest.hexdigest() != expected_sha:
            raise ValueError(f"SHA-256/tamanho inválido para o pacote {package['name']}")
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    return target


def verify_deb_metadata(deb: Path, package: dict) -> None:
    result = subprocess.run(
        ["dpkg-deb", "-f", str(deb), "Package", "Version", "Architecture"],
        check=True, capture_output=True, text=True,
    )
    actual = [line.split(":", 1)[1].strip() for line in result.stdout.splitlines() if ":" in line]
    expected = [package["name"], package["version"], package["architecture"]]
    if actual != expected:
        raise ValueError(f"Metadados inesperados em {deb.name}: {actual!r} != {expected!r}")


def needed_libraries(elf: Path) -> list[str]:
    result = subprocess.run(["readelf", "-d", str(elf)], check=True, capture_output=True, text=True)
    return re.findall(r"\(NEEDED\).*?Shared library: \[([^\]]+)\]", result.stdout)


def verify_android_aarch64(binary: Path) -> None:
    header = subprocess.run(["readelf", "-h", str(binary)], check=True, capture_output=True, text=True).stdout
    program = subprocess.run(["readelf", "-l", str(binary)], check=True, capture_output=True, text=True).stdout
    if "AArch64" not in header or "DYN" not in header:
        raise ValueError("O executável Termux não é ELF AArch64 PIE/ET_DYN.")
    if "/system/bin/linker64" not in program:
        raise ValueError("O executável não usa o linker64 do Android.")


def copy_runtime(extracted: Path, output: Path, lock: dict) -> list[dict]:
    prefix = extracted / TERMUX_PREFIX
    binary_source = prefix / "bin/qemu-system-aarch64"
    lib_source = prefix / "lib"
    if not binary_source.is_file() or not lib_source.is_dir():
        raise FileNotFoundError("O pacote Termux não contém o executável ou a pasta de bibliotecas esperada.")
    verify_android_aarch64(binary_source)

    shutil.rmtree(output, ignore_errors=True)
    (output / "bin").mkdir(parents=True, exist_ok=True)
    (output / "lib").mkdir(parents=True, exist_ok=True)
    binary_target = output / "bin/qemu-system-aarch64"
    shutil.copy2(binary_source, binary_target)
    binary_target.chmod(binary_target.stat().st_mode | 0o100)

    pending = [binary_source]
    visited: set[str] = set()
    while pending:
        elf = pending.pop()
        for name in needed_libraries(elf):
            if name in SYSTEM_LIBRARIES or name in visited:
                continue
            if Path(name).name != name or "/" in name or "\\" in name:
                raise ValueError(f"Nome de dependência ELF inválido: {name!r}")
            source = lib_source / name
            if not source.is_file():
                raise FileNotFoundError(f"Dependência ELF não encontrada nos pacotes: {name}")
            target = output / "lib" / name
            shutil.copy2(source.resolve(), target)
            visited.add(name)
            pending.append(source.resolve())

    files = []
    for base in (output / "bin", output / "lib"):
        for path in sorted(base.rglob("*")):
            if not path.is_file():
                continue
            files.append({
                "path": path.relative_to(output).as_posix(),
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
            })
    if not files or files[0]["path"] != "bin/qemu-system-aarch64":
        if not any(item["path"] == "bin/qemu-system-aarch64" for item in files):
            raise RuntimeError("O executável QEMU não entrou no manifesto gerado.")

    lock_bytes = LOCK_PATH.read_bytes()
    manifest = {
        "schemaVersion": 1,
        "version": "1:11.1.2-termux-aarch64",
        "binary": "bin/qemu-system-aarch64",
        "files": files,
        "source": {
            "qemuUrl": lock["qemuSourceUrl"],
            "qemuSha256": lock["qemuSourceSha256"],
            "termuxRecipeUrl": lock["termuxRecipeUrl"],
            "termuxRecipeCommitSha": lock["termuxRecipeCommitSha"],
            "termuxSubpackageUrl": lock["termuxSubpackageUrl"],
            "packageLockSha256": hashlib.sha256(lock_bytes).hexdigest(),
        },
    }
    (output / "runtime.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    copy_licenses(extracted, output, lock)
    return files


def copy_licenses(extracted: Path, output: Path, lock: dict) -> None:
    docs_root = extracted / TERMUX_PREFIX / "share/doc"
    licenses_root = output / "licenses"
    copied: list[tuple[str, str]] = []
    if docs_root.is_dir():
        for package in lock["packages"]:
            candidates = [docs_root / package["name"]]
            for doc_dir in candidates:
                if not doc_dir.is_dir():
                    continue
                for source in doc_dir.rglob("*"):
                    if not source.is_file():
                        continue
                    name = source.name.lower()
                    if name != "copyright" and not name.startswith(("license", "copying")):
                        continue
                    relative = source.relative_to(doc_dir)
                    target = licenses_root / package["name"] / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(source, target)
                    copied.append((package["name"], target.relative_to(output).as_posix()))
    lines = [
        "# Avisos de terceiros do runtime QEMU ARM64",
        "",
        "O runtime Android inclui QEMU 11.1.2 distribuído pelo pacote oficial Termux para AArch64 e as bibliotecas compartilhadas necessárias. QEMU é licenciado sob GPL-2.0; o código-fonte correspondente está disponível na URL abaixo e a receita Termux está no repositório citado.",
        "",
        f"- Fonte QEMU: {lock['qemuSourceUrl']} (SHA-256 `{lock['qemuSourceSha256']}`).",
        f"- Receita Termux: {lock['termuxRecipeUrl']}.",
        f"- Lock de pacotes: `scripts/qemu-termux-runtime.lock.json` no código-fonte do ZyX Virtual.",
        "- Os avisos de copyright/licença encontrados nos pacotes estão em `licenses/<pacote>/`.",
        "",
        "Pacotes e hashes dos arquivos `.deb` usados:",
        "",
    ]
    for package in lock["packages"]:
        lines.append(f"- `{package['name']} {package['version']}` — SHA-256 `{package['sha256']}`.")
    if copied:
        lines.extend(["", f"Avisos individuais copiados: {len(copied)} arquivo(s)."])
    (output / "LICENSES.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, default=WORK_DIR)
    parser.add_argument("--output-dir", type=Path, default=OUTPUT_DIR)
    args = parser.parse_args()
    if not LOCK_PATH.is_file():
        raise FileNotFoundError(f"Lockfile ausente: {LOCK_PATH}")
    lock_bytes = LOCK_PATH.read_bytes()
    lock = json.loads(lock_bytes)
    if lock.get("schemaVersion") != 1 or not lock.get("packages"):
        raise ValueError("Lockfile QEMU ausente ou incompatível.")
    root_package = next((p for p in lock["packages"] if p["name"] == lock["rootPackage"]), None)
    if not root_package or root_package["version"] != "1:11.1.2":
        raise ValueError("O lockfile não fixa o pacote QEMU 1:11.1.2 esperado.")
    for tool in ("dpkg-deb", "readelf"):
        if shutil.which(tool) is None:
            raise RuntimeError(f"Ferramenta obrigatória ausente: {tool}")

    work = args.work_dir.resolve()
    cache = work / "packages"
    extracted = work / "extracted"
    cache.mkdir(parents=True, exist_ok=True)
    packages = lock["packages"]
    print(f"Preparando QEMU Termux ARM64: {len(packages)} pacotes fixados ({sum(p['size'] for p in packages) / 1048576:.1f} MiB de download).", flush=True)
    downloaded: dict[str, Path] = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(download_package, package, lock["repository"], cache): package for package in packages}
        for index, future in enumerate(concurrent.futures.as_completed(futures), start=1):
            package = futures[future]
            downloaded[package["name"]] = future.result()
            if index % 10 == 0 or index == len(packages):
                print(f"Pacotes verificados: {index}/{len(packages)}", flush=True)

    shutil.rmtree(extracted, ignore_errors=True)
    extracted.mkdir(parents=True)
    for index, package in enumerate(packages, start=1):
        deb = downloaded[package["name"]]
        verify_deb_metadata(deb, package)
        subprocess.run(["dpkg-deb", "-x", str(deb), str(extracted)], check=True, stdout=subprocess.DEVNULL)
        if index % 20 == 0 or index == len(packages):
            print(f"Pacotes extraídos: {index}/{len(packages)}", flush=True)

    files = copy_runtime(extracted, args.output_dir.resolve(), lock)
    print(f"Runtime gerado: {len(files)} arquivos verificados em {args.output_dir.resolve()}", flush=True)
    print(f"Executável: {sum(item['size'] for item in files if item['path'].startswith('bin/')) / 1048576:.1f} MiB; bibliotecas: {sum(item['size'] for item in files if item['path'].startswith('lib/')) / 1048576:.1f} MiB", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Erro ao preparar runtime QEMU: {exc}", file=sys.stderr)
        raise SystemExit(1)
