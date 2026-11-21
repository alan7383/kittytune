"""Check Android string formats and prevent English fallback regressions.

Run from any directory: python scripts/check_translations.py [--base origin/main]
Missing translations are allowed: Android falls back to values/strings.xml.
"""

import argparse
from collections import Counter
from pathlib import Path
import re
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = "app/src/main/res"
# Catalog arguments use explicit positions or unnumbered %s/%d/%.2f.
# Do not treat prose such as "100% free" as a format argument.
FORMAT = re.compile(r"%%|(?<!\d)%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z])")


def string_elements(xml):
    result = {}
    for element in ET.fromstring(xml).findall("string"):
        name = element.attrib["name"]
        if name in result:
            raise ValueError(f"duplicate string: {name}")
        result[name] = element
    return result


def string_text(element):
    return "".join(element.itertext())


def strings(xml):
    return {name: string_text(element) for name, element in string_elements(xml).items()}


def normalized(text):
    text = unicodedata.normalize("NFC", text.strip())
    text = text.removeprefix('"').removesuffix('"') if text.startswith('"') and text.endswith('"') else text
    text = text.replace(r"\'", "'").replace(r'\"', '"').replace(r"\n", "\n")
    text = text.replace(r"\@", "@").replace(r"\?", "?")
    return " ".join(text.split())


def arguments(text):
    result = Counter()
    implicit_index = 0
    for match in FORMAT.finditer(text):
        if match.group() == "%%":
            continue
        index, conversion = match.groups()
        if index is None:
            implicit_index += 1
            index = str(implicit_index)
        result[index, conversion] += 1
    return result


def git_strings(ref, path):
    xml = subprocess.check_output(
        ["git", "show", f"{ref}:{path}"], cwd=ROOT, stderr=subprocess.DEVNULL
    )
    return strings(xml)


def check(base=None):
    source = strings((ROOT / RES / "values/strings.xml").read_bytes())
    catalogs = {
        path.parent.name: strings(path.read_bytes())
        for path in sorted((ROOT / RES).glob("values-*/strings.xml"))
    }
    errors = []
    previous_paths = {}
    previous_source = {}
    if base:
        previous_source = git_strings(base, f"{RES}/values/strings.xml")
        tree = subprocess.check_output(
            ["git", "ls-tree", "-r", "--name-only", base, RES], cwd=ROOT
        ).decode().splitlines()
        previous_paths = {
            Path(path).parent.name: path
            for path in tree
            if re.fullmatch(rf"{RES}/values-[^/]+/strings\.xml", path)
        }
    for locale in sorted(catalogs.keys() | previous_paths.keys()):
        catalog = catalogs.get(locale, {})
        generic = re.sub(r"-r[A-Z]{2}$", "", locale)
        if generic != locale and generic in catalogs:
            errors.append(f"{locale}: regional catalog overrides {generic}")
        previous = git_strings(base, previous_paths[locale]) if locale in previous_paths else {}
        for name, text in catalog.items():
            if name not in source:
                continue
            if arguments(text) != arguments(source[name]):
                errors.append(f"{locale}/{name}: format arguments differ from English")
            if normalized(source[name]) and not normalized(text):
                errors.append(f"{locale}/{name}: translation is empty")
            if "<unk>" in text and "<unk>" not in source[name]:
                errors.append(f"{locale}/{name}: untranslated token <unk> in translation")
        for name, text in previous.items():
            if name not in source or name not in previous_source:
                continue
            old = normalized(text)
            before = normalized(previous_source[name])
            after = normalized(catalog.get(name, source[name]))
            if old and old != before and after != old and after in {
                normalized(source[name]), before
            }:
                errors.append(f"{locale}/{name}: existing translation replaced by English")
    return catalogs, errors


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Git ref to check for translation regressions")
    args = parser.parse_args()
    catalogs, errors = check(args.base)
    for error in errors:
        print(error)
    print(f"Checked {len(catalogs)} locale catalogs: {len(errors)} error(s).")
    sys.exit(bool(errors))
