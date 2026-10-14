"""
Sends a small, scripted story of payments to a running Sentinel and prints what it decided.

    python simulator/traffic.py                 # app on http://localhost:8080
    python simulator/traffic.py --url http://localhost:9090

The story:
  1. five ordinary payments from five different customers     -> all APPROVE
  2. one Rs 75,000 payment                                     -> STEP_UP (amount rule)
  3. a card-testing burst: 8 tiny payments on one stolen card  -> APPROVE x5, then DECLINE
  4. the stored explanation for burst attempts #4 and #6, showing the shadow rule at work

Uses only Python's standard library, so there is nothing to install.
Stage 2 grows this into the full adversarial simulator (see simulator/README.md).
"""

import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone


def now_iso():
    # e.g. 2026-09-27T10:15:30Z, the format the API expects for occurredAt
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def call(method, url, body=None):
    """Makes one HTTP call. Returns (status, parsed JSON or None, milliseconds taken)."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            status, raw = resp.status, resp.read()
    except urllib.error.HTTPError as e:           # 4xx and 5xx still carry a JSON body
        status, raw = e.code, e.read()
    ms = (time.perf_counter() - start) * 1000
    try:
        parsed = json.loads(raw) if raw else None
    except ValueError:
        parsed = None
    return status, parsed, ms


def decide(base, label, txn, card, device, rupees, merchant="mer-demo"):
    body = {
        "transactionId": txn,
        "cardId": card,
        "deviceId": device,
        "merchantId": merchant,
        "amountMinor": rupees * 100,               # money travels as whole paise
        "currency": "INR",
        "occurredAt": now_iso(),
    }
    status, resp, ms = call("POST", base + "/v1/decisions", body)
    if status != 200:
        title = (resp or {}).get("title", "")
        print(f"  {label:<24} Rs {rupees:>8,}   HTTP {status} {title}")
        return None
    reasons = ", ".join(resp["reasonCodes"]) or "-"
    flag = "   (degraded)" if resp["degraded"] else ""
    print(f"  {label:<24} Rs {rupees:>8,}   {resp['outcome']:<8} {ms:6.1f} ms   {reasons}{flag}")
    return resp


def wait_for_quiet_moment():
    """
    The one-minute velocity window is really "this clock minute" (see RedisVelocityStore).
    If the burst started at hh:mm:58 it would be split across two minutes and could slip
    under the limit. That is a real weakness, fixed in stage 2; for a clean demo we just
    avoid the boundary.
    """
    second = datetime.now(timezone.utc).second
    if second >= 50:
        pause = 61 - second
        print(f"\n  (waiting {pause}s so the burst does not straddle a minute boundary)")
        time.sleep(pause)


def main():
    parser = argparse.ArgumentParser(description="Send a scripted story of payments to Sentinel.")
    parser.add_argument("--url", default="http://localhost:8080", help="where the app is running")
    args = parser.parse_args()
    base = args.url.rstrip("/")

    # A fresh suffix per run, so running the script twice gives fresh decisions rather
    # than the stored answers to the same transaction ids (that is idempotency at work).
    run = "%04x" % random.randrange(16 ** 4)

    try:
        call("GET", base + "/actuator/health")
    except urllib.error.URLError:
        print(f"Could not reach Sentinel at {base}. Is the app running? (./mvnw spring-boot:run)")
        sys.exit(1)

    print(f"\nRun {run}\n")
    print(f"  {'what':<24} {'amount':>11}   {'outcome':<8} {'client':>9}   reasons")
    print("  " + "-" * 78)

    print("  ordinary customers")
    for i, (name, rupees) in enumerate([("asha", 450), ("ravi", 1_299), ("meera", 2_150),
                                        ("kabir", 180), ("zoya", 4_500)], start=1):
        decide(base, f"{name} pays", f"txn-{run}-n{i}", f"card-{name}-{run}",
               f"dev-{name}-{run}", rupees)

    print("  a large purchase")
    decide(base, "priya buys a laptop", f"txn-{run}-big", f"card-priya-{run}", f"dev-priya-{run}", 75_000)

    print("  card testing: a stolen card, a script, tiny amounts")
    wait_for_quiet_moment()
    burst = []
    for i in range(1, 9):
        txn = f"txn-{run}-b{i}"
        decide(base, f"attempt #{i}", txn, f"card-stolen-{run}", f"dev-bot-{run}",
               random.randint(1, 99))
        burst.append(txn)
        time.sleep(0.2)

    print("\n  Why? The stored explanations (GET /v1/decisions/{id}):")
    for n in (4, 6):
        status, detail, _ = call("GET", f"{base}/v1/decisions/{burst[n - 1]}")
        if status != 200:
            print(f"    attempt #{n}: HTTP {status}")
            continue
        fired = [t["ruleCode"] + ("" if t["mode"] == "ACTIVE" else " (shadow)")
                 for t in detail["trace"] if t["result"] == "FIRED"]
        count = detail["features"].get("card.txnCount.1m")
        count_text = "not available (degraded)" if count is None else int(count)
        print(f"    attempt #{n}: outcome {detail['outcome']}, shadow outcome {detail['shadowOutcome']},"
              f" card.txnCount.1m = {count_text}, rules fired: {', '.join(fired) or 'none'}")

    print("\n  Try next:")
    print("    - docker stop sentinel-redis, run this again: every decision is 'degraded',")
    print("      the burst is no longer caught, and big payments are stepped up by DEGRADED_FALLBACK.")
    print("      That is the trade-off of degraded mode, made visible. docker start sentinel-redis after.")
    print("    - open http://localhost:8080/swagger-ui.html and look up any transaction id above.\n")


if __name__ == "__main__":
    main()
