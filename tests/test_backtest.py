import unittest

import backtest


class BacktestReportTest(unittest.TestCase):
    def test_world_cup_2018_known_metrics(self):
        summary, calibration, scorelines = backtest.analyse("world_cup_2018")

        self.assertEqual(48, summary["matches"])
        self.assertEqual(14, summary["correct_qualifiers"])
        self.assertEqual("France", summary["champion"])
        self.assertEqual(4, summary["champion_rank"])
        self.assertAlmostEqual(0.5102, summary["outcome_brier"], places=4)
        self.assertTrue(calibration)
        self.assertEqual(10, len(scorelines))


if __name__ == "__main__":
    unittest.main()
