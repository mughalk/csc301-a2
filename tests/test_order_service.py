import argparse
import json
import sys
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

import requests


@dataclass
class TestResult:
    name: str
    passed: bool
    expected_codes: List[int]
    actual_code: Optional[int]
    expected_body: Dict[str, Any]
    actual_body: Optional[Dict[str, Any]]
    error: Optional[str] = None


def load_json(path: str) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def parse_expected_codes_from_name(test_name: str) -> List[int]:
    """
    Derive expected HTTP status codes from keys like:
      - order_create_200
      - order_create_404_invalid_user_id
      - order_create_400,409_exceeded_quantity
    """
    tokens = test_name.split("_")
    for t in tokens:
        if t.isdigit():
            return [int(t)]
        if "," in t:
            parts = t.split(",")
            if all(p.isdigit() for p in parts):
                return [int(p) for p in parts]

    # Fallback: no status code found in name
    return []


def bodies_match(expected: Dict[str, Any], actual: Dict[str, Any]) -> Tuple[bool, str]:
    """
    Check that every key in expected exists in actual and matches by value.
    Allows actual to have extra keys.
    """
    for k, v in expected.items():
        if k not in actual:
            return False, f"Missing key '{k}' in response body"
        if actual[k] != v:
            return False, f"Key '{k}' mismatch: expected {v!r}, got {actual[k]!r}"
    return True, ""


def post_json(url: str, payload: Dict[str, Any], timeout: float) -> Tuple[int, Dict[str, Any]]:
    r = requests.post(url, json=payload, timeout=timeout)
    code = r.status_code
    try:
        body = r.json() if r.text else {}
    except Exception:
        body = {"_raw": r.text}
    return code, body


def run_tests(
        base_url: str,
        testcases: Dict[str, Dict[str, Any]],
        expected_responses: Dict[str, Dict[str, Any]],
        timeout: float,
        pause_s: float,
) -> List[TestResult]:
    results: List[TestResult] = []
    order_url = base_url.rstrip("/") + "/order"

    for name, payload in testcases.items():
        expected_body = expected_responses.get(name, {})
        expected_codes = parse_expected_codes_from_name(name)

        try:
            code, body = post_json(order_url, payload, timeout=timeout)
            ok_code = (not expected_codes) or (code in expected_codes)

            ok_body = True
            body_reason = ""
            if expected_body:
                ok_body, body_reason = bodies_match(expected_body, body)

            passed = ok_code and ok_body

            err = None
            if not ok_code:
                err = f"HTTP code mismatch: expected one of {expected_codes}, got {code}"
            elif not ok_body:
                err = body_reason

            results.append(
                TestResult(
                    name=name,
                    passed=passed,
                    expected_codes=expected_codes,
                    actual_code=code,
                    expected_body=expected_body,
                    actual_body=body,
                    error=err,
                )
            )
        except requests.RequestException as e:
            results.append(
                TestResult(
                    name=name,
                    passed=False,
                    expected_codes=expected_codes,
                    actual_code=None,
                    expected_body=expected_body,
                    actual_body=None,
                    error=f"Request failed: {e}",
                )
            )

        if pause_s > 0:
            time.sleep(pause_s)

    return results


def print_summary(results: List[TestResult]) -> int:
    total = len(results)
    passed = sum(1 for r in results if r.passed)
    failed = total - passed

    print("=" * 70)
    print(f"OrderService Test Seeing: {passed}/{total} passed, {failed} failed")
    print("=" * 70)

    for r in results:
        status = "PASS" if r.passed else "FAIL"
        print(f"[{status}] {r.name}")
        if not r.passed:
            print(f"  - Expected HTTP: {r.expected_codes if r.expected_codes else '(not specified)'}")
            print(f"  - Actual HTTP  : {r.actual_code}")
            print(f"  - Expected body keys: {list(r.expected_body.keys())}")
            print(f"  - Actual body       : {r.actual_body}")
            print(f"  - Error             : {r.error}")
        # keep output readable
    print("=" * 70)

    return 0 if failed == 0 else 1


def main() -> None:
    ap = argparse.ArgumentParser(description="OrderService test runner (POST /order)")
    ap.add_argument("--base-url", default="http://localhost:14002", help="OrderService base URL")
    ap.add_argument("--testcases", default="order_testcases.json", help="Path to order_testcases.json")
    ap.add_argument("--expected", default="order_responses.json", help="Path to order_responses.json")
    ap.add_argument("--timeout", type=float, default=3.0, help="HTTP timeout seconds")
    ap.add_argument("--pause", type=float, default=0.0, help="Pause between tests (seconds)")
    args = ap.parse_args()

    testcases = load_json(args.testcases)
    expected = load_json(args.expected)

    # Basic sanity: ensure every testcase has an expected response entry
    missing = [k for k in testcases.keys() if k not in expected]
    if missing:
        print("WARNING: Missing expected responses for:", missing)

    results = run_tests(
        base_url=args.base_url,
        testcases=testcases,
        expected_responses=expected,
        timeout=args.timeout,
        pause_s=args.pause,
    )
    exit_code = print_summary(results)
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
