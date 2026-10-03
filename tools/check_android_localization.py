#!/usr/bin/env python3
"""Check product translation keys, namespaces and positional Java-format argument contracts."""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PREFIXES = ('app_', 'camera_', 'measurement_', 'palette_', 'language_')
# These describe the report/transport schema, never resource names or translated prose.
MACHINE_KEYS = {'module_id', 'camera_model', 'palette_id', 'session_state', 'raw14', 'event', 'reason_code'}
FORMAT = re.compile(r'%(?:(\d+)\$)?([-#+ 0,(<]*)(\d+)?(?:\.(\d+))?([tT]?[a-zA-Z%])')


def parameters(text: str) -> dict[int, str]:
    """Require explicit positions and unchanged argument types, while allowing translated order."""
    result: dict[int, str] = {}
    for match in FORMAT.finditer(text):
        index, _, _, _, kind = match.groups()
        if kind in ('%', 'n'):
            continue
        if index is None:
            raise ValueError('format argument has no position')
        position = int(index)
        if position < 1 or (position in result and result[position] != kind):
            raise ValueError('conflicting argument type/position')
        result[position] = kind
    # A literal percent must be escaped for Android/Java formatting.
    if '%' in FORMAT.sub('', text):
        raise ValueError('unrecognized percent/format argument')
    return result


def entries(path: Path) -> dict[str, ET.Element]:
    """Retain each whole resource, so plurals are checked by quantity as well as string keys."""
    result = {}
    for element in ET.parse(path).getroot():
        if element.tag not in ('string', 'plurals'):
            continue
        key = element.attrib['name']
        if key in result:
            raise ValueError(f'duplicate key: {key}')
        result[key] = element
    return result


def audit(default: dict[str, ET.Element], translation: dict[str, ET.Element]) -> list[str]:
    """A nontranslatable resource must remain in default only; every translatable key needs French."""
    required = {key for key, value in default.items() if value.get('translatable') != 'false'}
    errors = [f'missing translation: {key}' for key in sorted(required - translation.keys())]
    errors += [f'unexpected translation: {key}' for key in sorted(translation.keys() - required)]
    for key in default:
        if not key.startswith(PREFIXES) or key in MACHINE_KEYS:
            errors.append(f'invalid product resource namespace: {key}')
    for key in sorted(required & translation.keys()):
        en, fr = default[key], translation[key]
        if en.tag != fr.tag:
            errors.append(f'{key}: resource type mismatch'); continue
        en_text = {'string': ''.join(en.itertext())} if en.tag == 'string' else {
            item.attrib['quantity']: ''.join(item.itertext()) for item in en}
        fr_text = {'string': ''.join(fr.itertext())} if fr.tag == 'string' else {
            item.attrib['quantity']: ''.join(item.itertext()) for item in fr}
        if not en_text.keys() <= fr_text.keys():
            errors.append(f'{key}: plural quantity mismatch')
        # Languages may need additional CLDR forms (French many); check their fallback argument contract too.
        for quantity in fr_text.keys():
            reference = en_text.get(quantity, en_text.get('other'))
            if reference is None or quantity not in {'string', 'zero', 'one', 'two', 'few', 'many', 'other'}:
                errors.append(f'{key}: invalid plural quantity {quantity}'); continue
            try:
                if parameters(reference) != parameters(fr_text[quantity]):
                    errors.append(f'{key}/{quantity}: argument mismatch')
            except ValueError as failure:
                errors.append(f'{key}/{quantity}: {failure}')
    return errors


class AuditTest(unittest.TestCase):
    """Deliberately broken resources prove the checker catches omissions rather than mirroring the product."""
    @staticmethod
    def resource(text):
        return {e.attrib['name']: e for e in ET.fromstring('<resources>'+text+'</resources>')}

    def test_missing_french_key_is_rejected(self):
        self.assertIn('missing translation: camera_connect', audit(self.resource(
            '<string name="camera_connect">Open</string>'), {}))

    def test_reordered_positional_arguments_are_allowed(self):
        self.assertEqual(parameters('%1$s %2$.2f'), parameters('%2$.2f %1$s'))

    def test_argument_type_change_is_rejected(self):
        self.assertTrue(audit(self.resource('<string name="camera_count">%1$d</string>'),
                              self.resource('<string name="camera_count">%1$s</string>')))

    def test_unpositioned_argument_is_rejected(self):
        with self.assertRaises(ValueError): parameters('%d')

    def test_conflicting_reuse_is_rejected(self):
        with self.assertRaises(ValueError): parameters('%1$d %1$s')

    def test_machine_key_and_unknown_namespace_are_rejected(self):
        self.assertTrue(audit(self.resource('<string name="module_id">ht301</string>'), {}))

    def test_translated_nontranslatable_key_is_rejected(self):
        self.assertTrue(audit(self.resource('<string name="app_brand" translatable="false">Brand</string>'),
                              self.resource('<string name="app_brand">Marque</string>')))

    def test_plural_quantity_and_parameters_are_checked(self):
        en = self.resource('<plurals name="camera_samples"><item quantity="one">%1$d sample</item>'
                           '<item quantity="other">%1$d samples</item></plurals>')
        self.assertFalse(audit(en, en))
        fr = self.resource('<plurals name="camera_samples"><item quantity="other">%1$s</item></plurals>')
        self.assertTrue(audit(en, fr))

    def test_additional_language_plural_form_uses_default_fallback_contract(self):
        en = self.resource('<plurals name="measurement_pixels"><item quantity="one">%1$d pixel</item>'
                           '<item quantity="other">%1$d pixels</item></plurals>')
        fr = self.resource('<plurals name="measurement_pixels"><item quantity="one">%1$d pixel</item>'
                           '<item quantity="many">%1$d pixels</item><item quantity="other">%1$d pixels</item></plurals>')
        self.assertFalse(audit(en, fr))

    def test_additional_plural_form_with_wrong_arguments_is_rejected(self):
        en = self.resource('<plurals name="measurement_pixels"><item quantity="other">%1$d pixels</item></plurals>')
        fr = self.resource('<plurals name="measurement_pixels"><item quantity="other">%1$d pixels</item>'
                           '<item quantity="many">%1$s pixels</item></plurals>')
        self.assertTrue(audit(en, fr))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true')
    parser.add_argument('--locale', action='append', help='values qualifier to check (default: fr)')
    options = parser.parse_args()
    if options.self_test and not unittest.TextTestRunner().run(
            unittest.defaultTestLoader.loadTestsFromTestCase(AuditTest)).wasSuccessful():
        return 1
    res = ROOT/'app/src/main/res'
    default = entries(res/'values/strings.xml')
    for locale in options.locale or ['fr']:
        translation = entries(res/f'values-{locale}/strings.xml')
        errors = audit(default, translation)
        if errors:
            print('\n'.join(errors), file=sys.stderr)
            return 1
        print(f'Localization parity passed: {len(default)} default / {len(translation)} {locale} keys; positional types and namespaces match.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
