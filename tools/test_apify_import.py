import json
from pathlib import Path
import tempfile
import unittest
from apify_import import validate_items, write_export


class ImportTest(unittest.TestCase):
    def test_actor_success_with_error_rows_is_failure(self):
        with self.assertRaisesRegex(ValueError, "NO ND script"):
            validate_items([{"url": "https://www.sofascore.com/football", "error": "NO ND script found"}])

    def test_empty_or_partial_dataset_does_not_replace_good_export(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "feed.json"
            target.write_text('[{"valid":true}]')
            for items in ([], [{"data": {}}, {"error": "unavailable"}]):
                with self.assertRaises(ValueError):
                    write_export(target, items)
                self.assertEqual(json.loads(target.read_text()), [{"valid": True}])

    def test_raw_data_can_be_exported_without_inventing_events(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "raw.json"
            write_export(target, [{"data": {"title": "Page"}}])
            self.assertEqual(json.loads(target.read_text()), [{"data": {"title": "Page"}}])


if __name__ == "__main__":
    unittest.main()
