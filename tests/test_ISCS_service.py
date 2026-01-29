import json
import urllib.request
import urllib.error
import sys

# --- CONFIGURATION ---
CONFIG_FILE = '../config.json'
USER_TESTCASES_FILE = 'user_testcases.json'
PRODUCT_TESTCASES_FILE = 'product_testcases.json'
OUTPUT_FILE = 'results_iscs.txt'

def load_config():
    """Load the ISCS port/ip from config.json"""
    try:
        with open(CONFIG_FILE, 'r') as f:
            config = json.load(f)
            iscs_conf = config.get("InterServiceCommunication")
            if not iscs_conf:
                print("Error: 'InterServiceCommunication' key not found in config.json")
                sys.exit(1)
            return iscs_conf
    except FileNotFoundError:
        print(f"Error: '{CONFIG_FILE}' not found.")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error: Failed to parse config JSON. {e}")
        sys.exit(1)


def load_test_cases(filepath):
    """Load test cases from a JSON file"""
    try:
        with open(filepath, 'r') as f:
            test_cases = json.load(f)
    except FileNotFoundError:
        print(f"Error: '{filepath}' not found.")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error: Failed to parse JSON in '{filepath}'. {e}")
        sys.exit(1)

    if not isinstance(test_cases, dict):
        print(f"Error: Expected '{filepath}' to contain a JSON object (dict).")
        sys.exit(1)

    return test_cases


def determine_service_type(test_name):
    """
    Determine if test is for user or product based on test name prefix.
    Returns: ('user' or 'product') and the endpoint path.
    """
    if test_name.startswith('user_'):
        return 'user', '/user'
    elif test_name.startswith('product_'):
        return 'product', '/product'
    else:
        return None, None


def build_request_for_iscs(service_type, endpoint, test_data, base_url):
    """
    Build an HTTP request to send through ISCS.
    ISCS should forward /user and /product requests to the appropriate services.
    """
    command = test_data.get("command")
    
    if command is not None:
        # POST request (create/update/delete)
        target_url = f"{base_url}{endpoint}"
        json_body = json.dumps(test_data)
        json_bytes = json_body.encode('utf-8')
        
        req = urllib.request.Request(
            target_url,
            data=json_bytes,
            headers={'Content-Type': 'application/json'},
            method="POST"
        )
        return req, "POST", json_body
    
    else:
        # GET request (retrieve)
        item_id = test_data.get("id")
        if item_id is None:
            return None, None, None
        
        target_url = f"{base_url}{endpoint}/{item_id}"
        req = urllib.request.Request(target_url, method="GET")
        return req, "GET", None


def run():
    # 1. Load config and ISCS URL
    iscs_conf = load_config()
    base_url = f"http://{iscs_conf['ip']}:{iscs_conf['port']}"
    
    # 2. Load test cases from both user and product files
    user_cases = load_test_cases(USER_TESTCASES_FILE)
    product_cases = load_test_cases(PRODUCT_TESTCASES_FILE)
    
    # Merge test cases (users first, then products, to maintain execution order)
    all_cases = {}
    all_cases.update(user_cases)
    all_cases.update(product_cases)
    
    print(f"--- TESTING ISCS ROUTING ---")
    print(f"Target ISCS: {base_url}")
    print(f"Total Tests: {len(all_cases)}")
    print(f"Note: UserService and ProductService MUST be running for routing to succeed.\n")

    with open(OUTPUT_FILE, 'w') as out:
        out.write("--- ISCS ROUTING TEST RESULTS ---\n\n")
        out.write(f"Total Tests: {len(all_cases)}\n\n")
        
        passed = 0
        failed = 0
        skipped = 0

        for test_name, test_data in all_cases.items():
            service_type, endpoint = determine_service_type(test_name)
            
            if service_type is None:
                out.write(f"=== TEST: {test_name} ===\n")
                out.write(f"SKIPPED: Unknown service type\n\n")
                skipped += 1
                print(f"[{test_name}] ⊘ SKIPPED (unknown service)")
                continue
            
            req, method, body = build_request_for_iscs(service_type, endpoint, test_data, base_url)
            
            if req is None:
                out.write(f"=== TEST: {test_name} ===\n")
                out.write(f"SKIPPED: Unable to build request\n\n")
                skipped += 1
                print(f"[{test_name}] ⊘ SKIPPED (no request)")
                continue
            
            out.write(f"=== TEST: {test_name} ===\n")
            out.write(f"SERVICE: {service_type.upper()}\n")
            out.write(f"METHOD: {method}\n")
            out.write(f"URL: {req.full_url}\n")
            if body:
                out.write(f"BODY: {body}\n")

            try:
                with urllib.request.urlopen(req) as response:
                    status = response.getcode()
                    response_body = response.read().decode('utf-8')
                    
                    out.write(f"STATUS: {status}\n")
                    out.write(f"RESPONSE: {response_body}\n")
                    out.write(f"RESULT: PASS\n")
                    print(f"[{test_name}] ✅ {status}")
                    passed += 1

            except urllib.error.HTTPError as e:
                # HTTP errors (400, 404, etc) mean routing worked
                # (the backend service received and processed the request)
                error_body = e.read().decode('utf-8')
                out.write(f"STATUS: {e.code}\n")
                out.write(f"ERROR BODY: {error_body}\n")
                out.write(f"RESULT: PASS (routing confirmed)\n")
                print(f"[{test_name}] ✅ {e.code} (routed)")
                passed += 1

            except Exception as e:
                # Connection errors mean ISCS is not running or not responding
                out.write(f"STATUS: CONNECTION FAILED\n")
                out.write(f"EXCEPTION: {str(e)}\n")
                out.write(f"RESULT: FAIL\n")
                print(f"[{test_name}] ❌ {type(e).__name__}")
                failed += 1

            out.write("\n" + "-"*40 + "\n\n")

        out.write(f"\nSummary:\n")
        out.write(f"  Passed/Routed: {passed}\n")
        out.write(f"  Failed: {failed}\n")
        out.write(f"  Skipped: {skipped}\n")
        print(f"\nDone. Results saved to {OUTPUT_FILE}")


if __name__ == "__main__":
    run()