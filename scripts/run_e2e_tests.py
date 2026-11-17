#!/usr/bin/env python3
"""
KittyTune Wear OS Companion — E2E Test Suite Runner
Executes Tiers 1-4 opaque-box test suites via Gradle, parses JUnit XML reports,
evaluates threshold criteria, and outputs formatted console report and JSON summary.
"""

import sys
import os
import subprocess
import xml.etree.ElementTree as ET
import json
import time

TIER_CLASSES = {
    "Tier 1 (Functional & Smoke)": "com.alananasss.kittytune.e2e.WearTier1FunctionalTest",
    "Tier 2 (Boundary & Edge Cases)": "com.alananasss.kittytune.e2e.WearTier2BoundaryAndEdgeTest",
    "Tier 3 (Pairwise & Combinatorial)": "com.alananasss.kittytune.e2e.WearTier3PairwiseTest",
    "Tier 4 (Workload & Scenarios)": "com.alananasss.kittytune.e2e.WearTier4WorkloadScenarioTest",
}

TIER_THRESHOLDS = {
    "Tier 1 (Functional & Smoke)": 55,
    "Tier 2 (Boundary & Edge Cases)": 55,
    "Tier 3 (Pairwise & Combinatorial)": 24,
    "Tier 4 (Workload & Scenarios)": 5,
}

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    gradlew = os.path.join(root_dir, "gradlew.bat" if os.name == "nt" else "gradlew")
    test_results_dir = os.path.join(root_dir, "app", "build", "test-results", "testDebugUnitTest")
    summary_json_path = os.path.join(root_dir, "app", "build", "reports", "e2e-summary.json")

    print("\n" + "=" * 70)
    print(" KITTYTUNE WEAR OS COMPANION — E2E TEST RUNNER")
    print(" Executing Tiers 1-4 Opaque-Box Test Suite")
    print("=" * 70)

    # 1. Run Gradle Test
    cmd = [gradlew, ":app:testDebugUnitTest", "--tests", "com.alananasss.kittytune.e2e.*"]
    print(f"\n[1/3] Running command: {' '.join(cmd)}")
    start_time = time.time()
    proc = subprocess.run(cmd, cwd=root_dir, capture_output=True, text=True)
    elapsed = time.time() - start_time

    if proc.returncode != 0:
        print("\n[ERROR] Gradle test task execution failed:")
        print(proc.stdout[-2000:] if len(proc.stdout) > 2000 else proc.stdout)
        print(proc.stderr[-2000:] if len(proc.stderr) > 2000 else proc.stderr)
        sys.exit(1)

    print(f" Gradle execution succeeded in {elapsed:.2f}s")

    # 2. Parse Test Results
    print(f"\n[2/3] Parsing JUnit XML reports in: {test_results_dir}")
    total_tests = 0
    total_failures = 0
    total_errors = 0
    total_skipped = 0
    tier_stats = {}

    for tier_name, class_name in TIER_CLASSES.items():
        xml_file = os.path.join(test_results_dir, f"TEST-{class_name}.xml")
        if not os.path.exists(xml_file):
            print(f"[WARN] Test report XML missing for {tier_name}: {xml_file}")
            tier_stats[tier_name] = {"tests": 0, "failures": 1, "errors": 0, "skipped": 0, "time": 0.0}
            total_failures += 1
            continue

        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()
            tests = int(root.attrib.get("tests", 0))
            failures = int(root.attrib.get("failures", 0))
            errors = int(root.attrib.get("errors", 0))
            skipped = int(root.attrib.get("skipped", 0))
            t_time = float(root.attrib.get("time", 0.0))

            tier_stats[tier_name] = {
                "tests": tests,
                "failures": failures,
                "errors": errors,
                "skipped": skipped,
                "time": t_time
            }

            total_tests += tests
            total_failures += failures
            total_errors += errors
            total_skipped += skipped
        except Exception as e:
            print(f"[ERROR] Failed parsing {xml_file}: {e}")
            tier_stats[tier_name] = {"tests": 0, "failures": 1, "errors": 0, "skipped": 0, "time": 0.0}
            total_failures += 1

    # 3. Formulate Summary and Verify Thresholds
    print("\n[3/3] Evaluating Coverage Thresholds:")
    all_passed = (total_failures == 0 and total_errors == 0 and total_tests > 0)
    thresholds_met = True

    print("-" * 70)
    print(f"{'Tier Name':<38} | {'Count':<7} | {'Target':<7} | {'Status':<8}")
    print("-" * 70)

    for tier_name, stats in tier_stats.items():
        target = TIER_THRESHOLDS.get(tier_name, 0)
        count = stats["tests"]
        met = (count >= target and stats["failures"] == 0 and stats["errors"] == 0)
        if not met:
            thresholds_met = False
        status_str = "PASS" if met else "FAIL"
        print(f"{tier_name:<38} | {count:<7} | {target:<7} | {status_str:<8}")

    print("-" * 70)
    print(f"Total Tests Executed: {total_tests}")
    print(f"Failures: {total_failures}, Errors: {total_errors}, Skipped: {total_skipped}")
    print(f"Total Execution Time: {elapsed:.2f}s")

    overall_verdict = all_passed and thresholds_met
    print("=" * 70)
    if overall_verdict:
        print(" FINAL VERDICT: 100% PASS (All thresholds satisfied)")
    else:
        print(" FINAL VERDICT: FAIL")
    print("=" * 70 + "\n")

    # Export JSON
    os.makedirs(os.path.dirname(summary_json_path), exist_ok=True)
    summary_data = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "total_tests": total_tests,
        "total_failures": total_failures,
        "total_errors": total_errors,
        "total_skipped": total_skipped,
        "overall_verdict": "PASS" if overall_verdict else "FAIL",
        "elapsed_seconds": round(elapsed, 2),
        "tiers": tier_stats,
        "thresholds_met": thresholds_met
    }
    with open(summary_json_path, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=2)

    sys.exit(0 if overall_verdict else 1)

if __name__ == "__main__":
    main()
