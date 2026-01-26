import json
import urllib.request
import urllib.error
import sys
from typing import Any, Dict, Optional, Tuple

# ---------------- CONFIG ----------------
TESTCASES_FILE = "product_testcases.json"
EXPECTED_FILE = "product_responses.json"
OUTPUT_FILE = "results.txt"

# If your ProductService is on 8082, change this
BASE_URL = "http://localhost:8082"

POST_ENDPOINT = "/product"         # POST create/update/delete
GET_ENDPOINT_PREFIX = "/product/"  # GET /product/<id>

PRICE_EPS = 1e-2  # float tolerance for price


# ---------------- HELPERS ----------------
def load_json_dict(path: str) -> Dict[str, Any]:
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        print(f"Error: '{path}' not found.")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error: Failed to parse JSON in '{path}': {e}")
        sys.exit(1)

    if not isinstance(data, dict):
        print(f"Error: Expected '{path}' to contain a JSON object (dict).")
        sys.exit(1)

    return data


def expected_status_from_test_name(test_name: str) -> Optional[int]:
    """
    Your keys look like:
      product_create_200_2000
      product_delete_404_401_fields_dont_match
    We grab the FIRST 3-digit token.
    """
    parts = test_name.split("_")
    for p in parts:
        if p.isdigit() and len(p) == 3:
            return int(p)
    return None


def build_request(case: Dict[str, Any]) -> Optional[urllib.request.Request]:
    command = case.get("command")

    if command is not None:
        # POST
        target_url = f"{BASE_URL}{POST_ENDPOINT}"
        body = json.dumps(case).encode("utf-8")
        return urllib.request.Request(
            target_url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )

    # GET
    pid = case.get("id")
    if pid is None:
        return None

    return urllib.request.Request(f"{BASE_URL}{GET_ENDPOINT_PREFIX}{pid}", method="GET")


def try_parse_json(text: str) -> Optional[Any]:
    try:
        return json.loads(text)
    except Exception:
        return None


def normalize_product_obj(obj: Any) -> Optional[Dict[str, Any]]:
    """
    Normalize server response and expected object into the same shape:
      {id, name, description, price, quantity}
    Server uses "productname"; expected file uses "name".
    """
    if not isinstance(obj, dict):
        return None

    out = dict(obj)

    # Normalize name field
    if "name" not in out and "productname" in out:
        out["name"] = out.get("productname")
    # (don’t delete productname; we just compare normalized "name")

    return out


def compare_products(expected: Dict[str, Any], actual: Dict[str, Any]) -> Tuple[bool, str]:
    """
    Compare only the fields we care about:
    id, name, description, price, quantity
    """
    diffs = []

    exp = normalize_product_obj(expected)
    act = normalize_product_obj(actual)

    if exp is None:
        return False, "Expected body is not an object."
    if act is None:
        return False, "Actual body is not an object / not JSON."

    # required fields in expected
    fields = ["id", "name", "description", "price", "quantity"]

    for f in fields:
        if f not in exp:
            # if expected omitted something, we don't check it
            continue

        if f not in act:
            diffs.append(f"missing field '{f}' in actual")
            continue

        if f == "price":
            try:
                ev = float(exp[f])
                av = float(act[f])
                if abs(ev - av) > PRICE_EPS:
                    diffs.append(f"price expected {ev} got {av}")
            except Exception:
                diffs.append(f"price expected {exp[f]} got {act[f]}")
        else:
            if exp[f] != act[f]:
                diffs.append(f"{f} expected {exp[f]!r} got {act[f]!r}")

    if diffs:
        return False, "; ".join(diffs)
    return True, "match"


# ---------------- RUNNER ----------------
def run():
    test_cases = load_json_dict(TESTCASES_FILE)
    expected_map = load_json_dict(EXPECTED_FILE)

    passed = 0
    failed = 0
    skipped = 0

    with open(OUTPUT_FILE, "w", encoding="utf-8") as out:
        out.write("--- TEST RUN RESULTS ---\n")
        out.write(f"Base URL: {BASE_URL}\n")
        out.write(f"Tests: {len(test_cases)}\n")
        out.write(f"Expected file keys: {len(expected_map)}\n\n")

        for test_name, case in test_cases.items():
            out.write(f"=== TEST: {test_name} ===\n")

            if not isinstance(case, dict):
                out.write("SKIPPED: testcase value is not an object.\n\n")
                print(f"[{test_name}] SKIPPED (not object)")
                skipped += 1
                continue

            req = build_request(case)
            if req is None:
                out.write("SKIPPED: No 'command' and no 'id'.\n\n")
                print(f"[{test_name}] SKIPPED (no command/id)")
                skipped += 1
                continue

            exp_status = expected_status_from_test_name(test_name)
            exp_body = expected_map.get(test_name)  # may be {} or missing

            # Log request
            out.write(f"REQUEST: {req.method} {req.full_url}\n")
            if req.method == "POST":
                out.write(f"SENT: {json.dumps(case)}\n")

            # Execute
            actual_status = None
            actual_text = ""
            try:
                with urllib.request.urlopen(req) as resp:
                    actual_status = resp.getcode()
                    actual_text = resp.read().decode("utf-8", errors="replace")
            except urllib.error.HTTPError as e:
                actual_status = e.code
                actual_text = e.read().decode("utf-8", errors="replace")
            except Exception as e:
                out.write("STATUS: CONNECTION FAILED\n")
                out.write(f"EXCEPTION: {e}\n\n")
                print(f"[{test_name}] 💥 CONNECTION FAILED: {e}")
                failed += 1
                continue

            out.write(f"STATUS: {actual_status}\n")
            out.write(f"BODY: {actual_text}\n")

            # ---------------- ASSERTIONS ----------------
            ok = True
            reasons = []

            # 1) Status compare (only if we can infer expected)
            if exp_status is not None and actual_status != exp_status:
                ok = False
                reasons.append(f"status expected {exp_status} got {actual_status}")

            # 2) Body compare (only when expected body is a non-empty object)
            # In your expected file, many failures are {} meaning "don't care body" :contentReference[oaicite:2]{index=2}
            if exp_body is None:
                # no expected entry found; only status check applied
                pass
            elif isinstance(exp_body, dict) and len(exp_body) == 0:
                # empty expected: skip body compare
                pass
            else:
                # try parse response as JSON and compare product fields
                actual_json = try_parse_json(actual_text)
                if actual_json is None:
                    ok = False
                    reasons.append("response is not valid JSON")
                elif not isinstance(exp_body, dict):
                    ok = False
                    reasons.append("expected body is not an object")
                else:
                    same, diff = compare_products(exp_body, actual_json)
                    if not same:
                        ok = False
                        reasons.append(diff)

            if ok:
                passed += 1
                out.write("RESULT: PASS\n\n")
                print(f"[{test_name}] ✅ PASS")
            else:
                failed += 1
                out.write("RESULT: FAIL\n")
                out.write("REASON: " + " | ".join(reasons) + "\n\n")
                print(f"[{test_name}] ❌ FAIL: {' | '.join(reasons)}")

        out.write("\n--- SUMMARY ---\n")
        out.write(f"PASSED: {passed}\nFAILED: {failed}\nSKIPPED: {skipped}\n")

    print(f"\nDone. Report saved to '{OUTPUT_FILE}'")
    print(f"Summary: PASSED={passed}, FAILED={failed}, SKIPPED={skipped}")


if __name__ == "__main__":
    run()
