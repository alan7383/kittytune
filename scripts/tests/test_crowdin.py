import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import check_translations as validator
from import_crowdin import apply_changes, changes


def catalog(**entries):
    xml = "<resources>" + "".join(f'<string name="{k}">{v}</string>' for k, v in entries.items()) + "</resources>"
    return validator.string_elements(xml)


class CrowdinImportTest(unittest.TestCase):
    def test_partial_export_keeps_omitted_strings_and_ignores_english(self):
        english = catalog(keep="Keep", exposed="Save", updated="Browse", new_key="Share")
        base = catalog(keep="Garder", exposed="Enregistrer", updated="Voir")
        incoming = catalog(exposed="  Save  ", updated="Parcourir", new_key="Partager")
        accepted, skipped = changes(english, english, base, base, incoming)

        self.assertEqual({"updated", "new_key"}, accepted.keys())
        self.assertEqual(1, skipped["empty or source text"])
        xml = '''<resources>
    <!-- Keep the local catalog's comments -->
    <string name="keep">Garder</string>
    <string name="exposed">Enregistrer</string>
    <string formatted="false" name="updated">Voir</string>
</resources>
'''
        merged = apply_changes(xml, base, accepted)
        self.assertIn('<!-- Keep the local catalog', merged)
        self.assertIn('<string formatted="false" name="updated">Parcourir</string>', merged)
        self.assertEqual(
            {"keep": "Garder", "exposed": "Enregistrer", "updated": "Parcourir", "new_key": "Partager"},
            validator.strings(merged),
        )

    def test_old_sources_local_edits_and_invalid_formats_are_left_alone(self):
        source = catalog(stale="New heading", edit="Settings", count="%1$d tracks", unknown="Artist")
        exported_source = catalog(stale="Old heading", edit="Settings", count="%1$d tracks", unknown="Artist")
        baseline = catalog(stale="Ancien titre", edit="Paramètres", count="%1$d titres", unknown="Interprète")
        current = catalog(stale="Nouveau titre", edit="Réglages", count="%1$d titres", unknown="Interprète")
        export = catalog(stale="Ancien texte", edit="Configuration", count="Titres", unknown="&lt;unk&gt;")
        accepted, skipped = changes(source, exported_source, baseline, current, export)
        self.assertFalse(accepted)
        self.assertEqual(
            {"source changed since export": 1, "local translation changed": 1,
             "format arguments differ": 1, "untranslated token": 1},
            skipped,
        )

    def test_validation_rejects_a_deleted_catalog_and_an_english_fallback(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            res = root / validator.RES
            (res / "values").mkdir(parents=True)
            (res / "values-fr").mkdir()
            (res / "values/strings.xml").write_text('<resources><string name="count">%1$d tracks</string></resources>')
            (res / "values-fr/strings.xml").write_text('<resources><string name="count"> "%1$d tracks" </string></resources>')
            before = {f"{validator.RES}/{folder}/strings.xml": {"count": string} for folder, string in [
                ("values", "%1$d tracks"), ("values-fr", "%1$d titres"), ("values-de", "%1$d Titel")
            ]}
            with patch.object(validator, "ROOT", root), \
                    patch.object(validator, "git_strings", side_effect=lambda ref, path: before[path]), \
                    patch.object(validator.subprocess, "check_output", return_value="\n".join(before).encode()):
                _, errors = validator.check("HEAD")
            self.assertIn("values-fr/count: existing translation replaced by English", errors)
            self.assertIn("values-de/count: existing translation replaced by English", errors)
