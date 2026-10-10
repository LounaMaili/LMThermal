"""Camera-free checks of the test-probe launch evidence validator."""
import unittest
from check_saf_launch import inspect


class SafLaunchTest(unittest.TestCase):
    def sample(self, button_bounds="[16,1800][1064,1900]", enabled="true"):
        return f'''<hierarchy><node text="R2 diagnostic SAF probe — no camera use" bounds="[16,80][1064,250]"/>
        <node text="Choose R2 test destination" bounds="{button_bounds}" enabled="{enabled}" clickable="true"/></hierarchy>'''

    def test_visible_separate_controls_pass(self):
        self.assertTrue(inspect(self.sample())["test_only_probe_visible"])

    def test_white_screen_hidden_button_and_overlap_fail(self):
        for xml in ("<hierarchy/>", self.sample("[0,0][0,0]"), self.sample("[16,100][1064,220]"), self.sample(enabled="false")):
            with self.assertRaises(ValueError):
                inspect(xml)


if __name__ == "__main__":
    unittest.main()
