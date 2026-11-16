"""Points every screen at the design drop-ins (Material 3 / Liquid Glass / MIUIX).

For each Kotlin file that calls one of the wrapped Material 3 components, replace the
Material import (explicit or via `material3.*`) with the drop-in from
`ui.design`. Explicit imports win over star imports in Kotlin, so nothing else
in the call sites changes. Safe to re-run.
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java/com/alananasss/kittytune'
PKG = 'com.alananasss.kittytune.ui.design'
NAMES = [
    'Button', 'FilledTonalButton', 'ElevatedButton', 'OutlinedButton', 'TextButton',
    'IconButton', 'FilledIconButton', 'FilledTonalIconButton', 'OutlinedIconButton',
    'Switch', 'Slider', 'FilterChip', 'InputChip', 'AssistChip', 'SuggestionChip', 'Card',
    'AlertDialog', 'DropdownMenu', 'ModalBottomSheet', 'FloatingActionButton',
    'SmallFloatingActionButton', 'ExtendedFloatingActionButton', 'TopAppBar',
    'CenterAlignedTopAppBar', 'LargeTopAppBar', 'MediumTopAppBar', 'OutlinedTextField',
    'Checkbox', 'TriStateCheckbox', 'RadioButton', 'Icon',
]
SKIP = {('KittyTextField.kt', 'OutlinedTextField')}

changed = 0
for dirpath, _, files in os.walk(ROOT):
    norm = dirpath.replace('/', os.sep)
    if norm.endswith(os.sep + 'ui' + os.sep + 'glass') or (os.sep + 'ui' + os.sep + 'glass' + os.sep) in norm + os.sep             or norm.endswith(os.sep + 'ui' + os.sep + 'design'):
        continue
    for f in files:
        if not f.endswith('.kt'):
            continue
        path = os.path.join(dirpath, f)
        src = open(path, encoding='utf-8').read()
        star = 'import androidx.compose.material3.*' in src
        body = re.sub(r'^import .*$', '', src, flags=re.M)
        new = src
        added = []
        for n in NAMES:
            if (f, n) in SKIP:
                continue
            explicit = re.search(r'^import androidx\.compose\.material3\.' + n + r'\s*$', new, re.M)
            if not (star or explicit):
                continue
            if re.search(r'^import ' + re.escape(PKG) + r'\.' + n + r'\s*$', new, re.M):
                continue
            # Called unqualified somewhere (not as material3.X(...), not a definition).
            if not re.search(r'(?<![\w.])' + n + r'\s*[({]', body):
                continue
            if re.search(r'fun\s+(?:[\w.]+\.)?' + n + r'\s*\(', body):
                continue
            # Another explicit import with the same simple name would clash.
            other = re.search(r'^\s*import (?!androidx\.compose\.material3\.)(?!' + re.escape(PKG) + r')[\w.]+\.' + n + r'\s*$', new, re.M)
            if other:
                continue
            if explicit:
                new = new[:explicit.start()] + 'import ' + PKG + '.' + n + new[explicit.end():]
            else:
                added.append(n)
        if added:
            m = list(re.finditer(r'^import .*$', new, re.M))
            if m:
                pos = m[-1].end()
                new = new[:pos] + ''.join('\nimport ' + PKG + '.' + n for n in added) + new[pos:]
        if new != src:
            open(path, 'w', encoding='utf-8').write(new)
            changed += 1
print('files changed:', changed)
