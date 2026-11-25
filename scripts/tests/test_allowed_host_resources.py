import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


RES = Path(__file__).resolve().parents[2] / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
HINT = "list_dialog_hostname_hint"


class AllowedHostResourcesTest(unittest.TestCase):
    def test_every_configuration_inherits_one_hostname_example(self):
        definitions = []
        for path in RES.glob("values*/*.xml"):
            for node in ET.parse(path).getroot():
                if node.get("name") == HINT and (
                    node.tag == "string" or (node.tag == "item" and node.get("type") == "string")
                ):
                    definitions.append((path.parent.name, node.get("translatable"), node.text))
        self.assertEqual(definitions, [("values", "false", "example.org")])

    def test_localized_label_targets_the_field_with_the_example(self):
        layouts = list(RES.glob("layout*/lists_allowed_dialog.xml"))
        self.assertTrue(layouts)
        for path in layouts:
            with self.subTest(path=path):
                root = ET.parse(path).getroot()
                field = root.find(f".//EditText[@{ANDROID}id='@+id/list_dialog_hostname']")
                self.assertIsNotNone(field)
                self.assertEqual(field.get(ANDROID + "hint"), "@string/" + HINT)
                label = root.find(f".//TextView[@{ANDROID}text='@string/list_dialog_hostname']")
                self.assertIsNotNone(label)
                self.assertIn(label.get(ANDROID + "labelFor"),
                              ("@id/list_dialog_hostname", "@+id/list_dialog_hostname"))


if __name__ == "__main__":
    unittest.main()
