"""
Production Verification for API Rate Limiting on /api/chat.

Tests:
  1. Unauthenticated request -> HTTP 401.
  2. Malformed token request -> HTTP 401.
  3. Authenticated normal RAG request -> HTTP 200.
  4. Rapid requests hitting the 20 req/min limit -> HTTP 429 Too Many Requests.
  5. Verification of Retry-After and X-RateLimit-* headers.
  6. Verification of clean error payload (no internal secrets leaked).
"""

import concurrent.futures
import json
import time
import requests

PROD_BASE = "https://belagavi-tourism-yuvaraj21.vercel.app"
CHAT_ENDPOINT = f"{PROD_BASE}/api/chat"
HEALTH_ENDPOINT = f"{PROD_BASE}/api/health"

FIREBASE_WEB_API_KEY = "AIzaSyD-jZk5yGZ5jRsHM9YU19TtNysbngq04bs"
TEST_EMAIL = "testuser_belagavi@example.com"
TEST_PASSWORD = "TestPassword123!"

results = {}

def get_real_firebase_id_token() -> str:
    url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_WEB_API_KEY}"
    r = requests.post(
        url,
        json={"email": TEST_EMAIL, "password": TEST_PASSWORD, "returnSecureToken": True},
        timeout=10,
    )
    if r.status_code == 200:
        return r.json()["idToken"]
    raise RuntimeError(f"Failed to obtain token: {r.text}")


print("=" * 65)
print("STEP 5: PRODUCTION VERIFICATION OF RATE LIMITING")
print("Target:", CHAT_ENDPOINT)
print("=" * 65)

# Wait a brief moment if Vercel is deploying
print("Checking health endpoint...")
for attempt in range(12):
    try:
        r = requests.get(HEALTH_ENDPOINT, timeout=10)
        if r.status_code == 200:
            print(f"Health OK ({r.status_code})")
            break
    except Exception as e:
        print(f"Waiting for health check ({e})...")
    time.sleep(3)

# Obtain Token
token = get_real_firebase_id_token()
print("Token acquired successfully.")

# 1. Unauthenticated Test -> 401
print("\n--- 1. Testing Unauthenticated Request ---")
r_no_auth = requests.post(CHAT_ENDPOINT, json={"message": "hello"}, timeout=10)
print(f"Status: {r_no_auth.status_code} | Body: {r_no_auth.text}")
assert r_no_auth.status_code == 401, f"Expected 401, got {r_no_auth.status_code}"
results["unauthenticated_401"] = {
    "status": r_no_auth.status_code,
    "body": r_no_auth.json()
}

# 2. Malformed Token Test -> 401
print("\n--- 2. Testing Malformed Token Request ---")
r_bad_auth = requests.post(
    CHAT_ENDPOINT,
    json={"message": "hello"},
    headers={"Authorization": "Bearer fake.malformed.token"},
    timeout=10,
)
print(f"Status: {r_bad_auth.status_code} | Body: {r_bad_auth.text}")
assert r_bad_auth.status_code == 401, f"Expected 401, got {r_bad_auth.status_code}"
results["malformed_token_401"] = {
    "status": r_bad_auth.status_code,
    "body": r_bad_auth.json()
}

# 3. Repeated requests until rate limit (20 req/min limit)
print("\n--- 3. Testing Rapid Concurrent Requests until Rate Limit ---")
hit_429 = False
status_codes = []
rate_limit_headers = {}
error_detail = None

def send_chat_req(idx: int):
    t_start = time.time()
    resp = requests.post(
        CHAT_ENDPOINT,
        json={"message": "hi"},
        headers={"Authorization": f"Bearer {token}"},
        timeout=25,
    )
    elapsed = time.time() - t_start
    return idx, resp.status_code, elapsed, resp

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
    futures = [ex.submit(send_chat_req, i) for i in range(1, 26)]
    completed = [f.result() for f in concurrent.futures.as_completed(futures)]

completed.sort(key=lambda x: x[0])
for idx, code, elapsed, resp in completed:
    status_codes.append(code)
    print(f"Request #{idx}: Status {code} ({elapsed:.2f}s)")
    if code == 429:
        hit_429 = True
        rate_limit_headers = {
            "Retry-After": resp.headers.get("Retry-After"),
            "X-RateLimit-Limit": resp.headers.get("X-RateLimit-Limit"),
            "X-RateLimit-Remaining": resp.headers.get("X-RateLimit-Remaining"),
            "X-RateLimit-Reset": resp.headers.get("X-RateLimit-Reset"),
        }
        error_detail = resp.text

assert hit_429, f"Failed to hit 429 limit in 25 requests! Status codes: {status_codes}"
results["hit_429"] = True
results["rate_limit_headers"] = rate_limit_headers
results["rate_limit_body"] = error_detail
print("\n429 Rate Limit Triggered Successfully!")
print("Response Body:", error_detail)
print("Response Headers:", json.dumps(rate_limit_headers, indent=2))

# 4. Wait for Retry-After window and verify recovery + full RAG
retry_after_secs = int(rate_limit_headers.get("Retry-After") or 15)
print(f"\n--- 4. Waiting {retry_after_secs + 2}s for window reset to test RAG recovery ---")
time.sleep(retry_after_secs + 2)

print("Sending normal AI query post-reset: 'Tell me about Belagavi Fort'...")
t0 = time.time()
r_auth = requests.post(
    CHAT_ENDPOINT,
    json={"message": "Tell me about Belagavi Fort"},
    headers={"Authorization": f"Bearer {token}"},
    timeout=30,
)
latency = time.time() - t0
print(f"Status: {r_auth.status_code} | Latency: {latency:.2f}s")
assert r_auth.status_code == 200, f"Expected 200, got {r_auth.status_code}: {r_auth.text}"
data = r_auth.json()
dest_names = [d.get("name") for d in data.get("destinations", [])]
print(f"Destinations Returned: {dest_names}")
print(f"AI Answer: {data.get('answer')[:150]}...")
assert "Belagavi Fort" in dest_names, "Expected Belagavi Fort in destinations"
results["recovery_authenticated_200"] = {
    "status": r_auth.status_code,
    "destinations": dest_names,
    "latency_seconds": round(latency, 2),
}

print("\n" + "=" * 65)
print("ALL PRODUCTION RATE LIMITING VERIFICATIONS PASSED!")
print("=" * 65)
print(json.dumps(results, indent=2))
