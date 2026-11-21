"""Preview a Crowdin Git export. Use --apply after reviewing the proposed changes."""

import argparse
from collections import Counter
import re
import subprocess
from xml.sax.saxutils import escape
import xml.etree.ElementTree as ET

from check_translations import RES, ROOT, arguments, normalized, string_elements, string_text

STRING_XML = re.compile(
    r'(<string(?=\s)(?:"[^"]*"|\'[^\']*\'|[^\'"<>])*(?<!/)>)(.*?)(</string\s*>)',
    re.DOTALL,
)
NAME_ATTR = re.compile(r"""\sname\s*=\s*(["'])(.*?)\1""")


def changes(source, exported_source, base, current, incoming):
    accepted = {}
    skipped = Counter()
    for name, element in incoming.items():
        if name not in source or name not in exported_source:
            skipped["unknown key"] += 1
            continue

        candidate = normalized(string_text(element))
        english = normalized(string_text(source[name]))
        old_english = normalized(string_text(exported_source[name]))
        previous = normalized(string_text(base[name])) if name in base else None
        existing = normalized(string_text(current[name])) if name in current else None
        if candidate in (existing, previous):
            continue

        if not candidate or candidate in (english, old_english):
            skipped["empty or source text"] += 1
        elif english != old_english:
            skipped["source changed since export"] += 1
        elif existing is not None and existing != previous and existing != english:
            skipped["local translation changed"] += 1
        elif arguments(string_text(element)) != arguments(string_text(source[name])):
            skipped["format arguments differ"] += 1
        elif "<unk>" in candidate and "<unk>" not in english:
            skipped["untranslated token"] += 1
        else:
            accepted[name] = element
    return accepted, skipped


def apply_changes(xml, current, accepted):
    if not accepted:
        return xml
    remaining = dict(accepted)

    def replace(match):
        opening, _, closing = match.groups()
        name = NAME_ATTR.search(opening)
        if name is None or name.group(2) not in remaining:
            return match.group(0)
        element = remaining.pop(name.group(2))
        text = escape(element.text or "") + "".join(ET.tostring(child, encoding="unicode") for child in element)
        return opening + text + closing

    result = STRING_XML.sub(replace, xml)
    if current.keys() & remaining.keys():
        raise ValueError("Could not replace an existing string without rewriting the catalog")
    if remaining:
        extra = "\n".join("    " + ET.tostring(element, encoding="unicode").rstrip() for element in remaining.values())
        before, closing = result.rsplit("</resources>", 1)
        result = before.rstrip("\n") + "\n" + extra + "\n</resources>" + closing
    string_elements(result)
    return result


def git(*command):
    return subprocess.check_output(["git", *command], cwd=ROOT, text=True).strip()


def catalog_at(ref, path):
    result = subprocess.run(
        ["git", "show", f"{ref}:{path}"], cwd=ROOT, capture_output=True, check=False
    )
    return string_elements(result.stdout) if result.returncode == 0 else {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ref", help="Crowdin Git export, for example origin/l10n_main")
    parser.add_argument("--apply", action="store_true", help="Write the reviewed changes")
    args = parser.parse_args()

    ref = git("rev-parse", "--verify", "--end-of-options", f"{args.ref}^{{commit}}")
    ancestor = git("merge-base", "HEAD", ref)
    source_path = f"{RES}/values/strings.xml"
    source = string_elements((ROOT / source_path).read_bytes())
    exported_source = catalog_at(ref, source_path)
    if not exported_source:
        parser.error("The export ref must include its English source catalog.")

    pending = {}
    for path in sorted((ROOT / RES).glob("values-*/strings.xml")):
        relative = path.relative_to(ROOT).as_posix()
        raw = path.read_text(encoding="utf-8")
        current = string_elements(raw)
        accepted, skipped = changes(
            source, exported_source, catalog_at(ancestor, relative), current, catalog_at(ref, relative)
        )
        if accepted:
            pending[path] = apply_changes(raw, current, accepted)
        flags = ", ".join(f"{amount} {reason}" for reason, amount in sorted(skipped.items()))
        print(f"{path.parent.name}: {len(accepted)} update(s)" + (f"; skipped {flags}" if flags else ""))
        if not args.apply:
            for name in sorted(accepted):
                print(f"  {name}: {string_text(current[name]) if name in current else '(new)'} → {string_text(accepted[name])}")

    if args.apply:
        for path, xml in pending.items():
            path.write_text(xml, encoding="utf-8")
    print(f"{len(pending)} catalog(s) {'updated' if args.apply else 'would change'}.")


if __name__ == "__main__":
    main()
