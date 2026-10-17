#!/usr/bin/env python3
"""Gera strings Android em português brasileiro e o localeConfig do app.
Execute: python app/l10n/gen.py."""
import os, runpy, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "res")
sys.path.insert(0, HERE)
from keys import KEYS  # noqa: E402

# Código BCP-47 → diretório de recursos Android.
DIRS = {"pt-BR": "values-pt-rBR"}


def esc(s: str) -> str:
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    return s


def load(code):
    return runpy.run_path(os.path.join(HERE, "langs", code + ".py"))["S"]


def main():
    en = load("en")
    missing = [k for k in KEYS if k not in en]
    assert not missing, f"Catálogo de referência sem chaves: {missing}"
    for code, d in DIRS.items():
        s = load(code)
        lack = [k for k in KEYS if k not in s]
        extra = [k for k in s if k not in KEYS]
        if lack or extra:
            print(f"{code}: faltando {lack}; extras {extra}")
        out = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- gerado por app/l10n/gen.py; edite langs/*.py -->", "<resources>"]
        if code == "en":
            out.append('    <string name="app_name" translatable="false">ZyX Virtual</string>')
        for k in KEYS:
            if k in s:
                out.append(f'    <string name="{k}">{esc(s[k])}</string>')
        out.append("</resources>\n")
        path = os.path.join(RES, d)
        os.makedirs(path, exist_ok=True)
        with open(os.path.join(path, "strings.xml"), "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(out))
    # The app is Portuguese-only; keep the default resource fallback in pt-BR too.
    pt = load("pt-BR")
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- gerado por app/l10n/gen.py -->", "<resources>"]
    out.append('    <string name="app_name" translatable="false">ZyX Virtual</string>')
    out.extend(f'    <string name="{k}">{esc(pt[k])}</string>' for k in KEYS)
    out.append("</resources>\n")
    with open(os.path.join(RES, "values", "strings.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out))

    tags = [c for c in DIRS]
    xml = ['<?xml version="1.0" encoding="utf-8"?>', '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">']
    xml += [f'    <locale android:name="{t}" />' for t in tags]
    xml.append("</locale-config>\n")
    os.makedirs(os.path.join(RES, "xml"), exist_ok=True)
    with open(os.path.join(RES, "xml", "locales_config.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(xml))
    print(f"Concluído: {len(DIRS)} idioma(s), {len(KEYS)} strings.")


if __name__ == "__main__":
    main()
