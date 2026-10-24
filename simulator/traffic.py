"""
Sends payments to a running Sentinel, the way a payment system would, and prints what it decided.

    py simulator\\traffic.py                  the scripted story: about 20 payments, with explanations
    py simulator\\traffic.py normal           ordinary customers, until you press Ctrl+C
    py simulator\\traffic.py card-testing     one stolen card tried again and again
    py simulator\\traffic.py enumeration      one bot device working through many stolen cards
    py simulator\\traffic.py takeover         known customers, then large payments from a new device

Options
    --url URL        where Sentinel runs (default http://localhost:8080)
    --api-key KEY    the API key (default: SENTINEL_API_KEY, else the local dev key)
    --rate N         payments per second for "normal" (default 2)
    --seconds N      stop "normal" after N seconds (default: run until Ctrl+C)
    --quiet          print only the summary

For a demo: run "normal" in one terminal, open the console's Live page, then start an attack
from a second terminal and watch it appear.

The simulator knows which payments are attacks, so it ends with a scorecard: what Sentinel
did to ordinary payments, and what it did to attacks. That ground truth is what will let us
measure rules properly later.

Only Python's standard library is used, so there is nothing to install.
"""

import argparse
import json
import os
import random
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

DEFAULT_KEY = "dev-sentinel-key"

MERCHANTS = ["mer-chai-point", "mer-metro-mart", "mer-bookhive", "mer-cityrail", "mer-greenbasket",
             "mer-fixit", "mer-tiffin-box", "mer-sportzone", "mer-pharmaplus", "mer-cinemax"]
NAMES = ["asha", "ravi", "meera", "kabir", "zoya", "dev", "isha", "arjun", "tara", "nikhil",
         "sana", "vikram", "priya", "rohan", "neha", "aditya", "kavya", "farhan", "simran", "yash"]

print_lock = threading.Lock()
key_hint = threading.Event()   # set once the "API key refused" hint has been printed


def show(line=""):
    with print_lock:
        print(line, flush=True)


def now_iso():
    # e.g. 2026-10-23T10:15:30Z, the format the API expects for occurredAt
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class Sentinel:
    """Talks to the API. Every call carries the API key, like a real payment system's would."""

    def __init__(self, url, api_key):
        self.base = url.rstrip("/")
        self.api_key = api_key

    def call(self, method, path, body=None):
        """Makes one HTTP call. Returns (status, parsed JSON or None, milliseconds taken)."""
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method, headers={
            "Content-Type": "application/json",
            "X-Api-Key": self.api_key,
        })
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:       # 4xx and 5xx still carry a JSON body
            status, raw = error.code, error.read()
        ms = (time.perf_counter() - start) * 1000
        try:
            parsed = json.loads(raw) if raw else None
        except ValueError:
            parsed = None
        return status, parsed, ms

    def reachable(self):
        try:
            self.call("GET", "/actuator/health")
            return True
        except urllib.error.URLError:
            return False


class Scorecard:
    """Counts outcomes separately for ordinary payments and attacks. Safe to use from many threads."""

    def __init__(self):
        self.lock = threading.Lock()
        self.counts = {}

    def add(self, kind, outcome):
        with self.lock:
            self.counts[(kind, outcome)] = self.counts.get((kind, outcome), 0) + 1

    def report(self):
        show()
        show(f"  {'':<10} {'sent':>6} {'approved':>9} {'step-up':>8} {'declined':>9} {'errors':>7}")
        for kind in ("ordinary", "attack"):
            row = [self.counts.get((kind, o), 0) for o in ("APPROVE", "STEP_UP", "DECLINE", "ERROR")]
            if sum(row):
                show(f"  {kind:<10} {sum(row):>6} {row[0]:>9} {row[1]:>8} {row[2]:>9} {row[3]:>7}")
        show()


def pay(api, score, kind, label, txn, card, device, rupees, merchant, quiet=False):
    """Sends one payment and prints one line about it. Returns the decision, or None."""
    body = {
        "transactionId": txn,
        "cardId": card,
        "deviceId": device,
        "merchantId": merchant,
        "amountMinor": rupees * 100,               # money travels as whole paise
        "currency": "INR",
        "occurredAt": now_iso(),
    }
    try:
        status, decision, ms = api.call("POST", "/v1/decisions", body)
    except urllib.error.URLError as error:
        score.add(kind, "ERROR")
        show(f"  {label:<26} could not reach Sentinel ({error.reason})")
        return None

    if status != 200:
        score.add(kind, "ERROR")
        title = (decision or {}).get("title", "")
        show(f"  {label:<26} Rs {rupees:>8,}   HTTP {status} {title}")
        if status == 401 and not key_hint.is_set():
            key_hint.set()
            show("  (the API key was refused: pass --api-key, or set SENTINEL_API_KEY)")
        return None

    score.add(kind, decision["outcome"])
    if not quiet:
        reasons = ", ".join(decision["reasonCodes"]) or "-"
        degraded = "   (degraded)" if decision["degraded"] else ""
        show(f"  {label:<26} Rs {rupees:>8,}   {decision['outcome']:<8} {ms:6.0f} ms   {reasons}{degraded}")
    return decision


def explain(api, txn, label):
    """Fetches a stored decision and prints which rules fired, including shadow rules."""
    status, detail, _ = api.call("GET", f"/v1/decisions/{txn}")
    if status != 200:
        show(f"    {label}: HTTP {status}")
        return
    fired = [t["ruleCode"] + ("" if t["mode"] == "ACTIVE" else " (shadow)")
             for t in detail["trace"] if t["result"] == "FIRED"]
    show(f"    {label}: outcome {detail['outcome']}, shadow outcome {detail['shadowOutcome']},"
         f" rules fired: {', '.join(fired) or 'none'}")


def header():
    show(f"  {'payment':<26} {'amount':>11}   {'outcome':<8} {'time':>9}   reasons")
    show("  " + "-" * 82)


def wait_for_quiet_moment():
    """
    The one-minute velocity window is really "this clock minute" (see RedisVelocityStore).
    A burst that starts at hh:mm:55 is split across two minutes and can slip under the
    limit. That weakness is on the list for after the exams; for a clean demo we just
    avoid the boundary.
    """
    second = datetime.now(timezone.utc).second
    if second >= 45:
        pause = 61 - second
        show(f"  (waiting {pause}s so the burst does not straddle a minute boundary)")
        time.sleep(pause)


def run_id():
    # A fresh suffix per run: running twice gives fresh decisions rather than the stored
    # answers to the same transaction ids (that would be idempotency at work).
    return "%04x" % random.randrange(16 ** 4)


# ---- scenarios ------------------------------------------------------------------------

def everyday_rupees():
    """Most payments are small and a few are large: a log-normal spread around Rs 900."""
    return max(20, min(int(random.lognormvariate(6.8, 0.9)), 45_000))


def normal(api, score, args):
    """
    Ordinary customers, each with their own card and phone. A thousand of them, so that at
    two payments a second each customer pays only a few times an hour, like real people.
    Now and then someone buys something expensive, which the amount rule steps up: that is
    the system working, not an attack.
    """
    run = run_id()
    customers = [(f"{NAMES[i % len(NAMES)]}{i // len(NAMES)}", random.sample(MERCHANTS, 3)) for i in range(1000)]
    show(f"\nOrdinary traffic, run {run}: {args.rate} payments a second. Ctrl+C to stop.\n")
    if not args.quiet:
        header()

    interval = 1.0 / args.rate
    deadline = time.monotonic() + args.seconds if args.seconds else None
    next_at = time.monotonic()
    n = 0
    pool = ThreadPoolExecutor(max_workers=8)       # keeps the rate up even when each call is slow
    try:
        while deadline is None or time.monotonic() < deadline:
            n += 1
            name, usual = random.choice(customers)
            rupees = random.randint(55_000, 90_000) if random.random() < 0.015 else everyday_rupees()
            pool.submit(pay, api, score, "ordinary", f"{name} pays", f"txn-{run}-n{n}",
                        f"card-{name}-{run}", f"dev-{name}-{run}", rupees, random.choice(usual), args.quiet)
            next_at += interval
            time.sleep(max(0.0, next_at - time.monotonic()))
    except KeyboardInterrupt:
        show("\n  stopping...")
    finally:
        pool.shutdown(wait=True, cancel_futures=True)


def card_testing(api, score, args):
    """
    One stolen card, tried again and again with tiny amounts by a script checking whether
    it works. VEL_CARD_1M declines it from the sixth attempt in a minute; the stricter
    shadow rule would have stopped it from the fourth.
    """
    run = run_id()
    show(f"\nCard testing, run {run}: one stolen card, ten quick attempts.\n")
    header()
    wait_for_quiet_moment()
    attempts = []
    for i in range(1, 11):
        txn = f"txn-{run}-t{i}"
        pay(api, score, "attack", f"attempt #{i}", txn, f"card-stolen-{run}", f"dev-script-{run}",
            random.randint(1, 99), "mer-fixit")
        attempts.append(txn)
        time.sleep(0.3)
    show("\n  The stored explanations:")
    explain(api, attempts[3], "attempt #4")
    explain(api, attempts[5], "attempt #6")


def enumeration(api, score, args):
    """
    One bot device working through a list of stolen card numbers, one small payment per
    card. No single card is used twice, so the card velocity rules never notice. The
    distinct-cards signal does: the shadow rule DEVICE_MANY_CARDS_1H would decline from the
    fifth card, and DEVICE_BURST_1H steps up from the 21st payment on the device.
    """
    run = run_id()
    show(f"\nCard enumeration, run {run}: one device, 25 different stolen cards.\n")
    header()
    attempts = []
    for i in range(1, 26):
        txn = f"txn-{run}-e{i}"
        pay(api, score, "attack", f"card #{i}", txn, f"card-list-{run}-{i}", f"dev-bot-{run}",
            random.randint(1, 99), "mer-sportzone")
        attempts.append(txn)
        time.sleep(0.25)
    show("\n  The stored explanations:")
    explain(api, attempts[3], "card #4")
    explain(api, attempts[4], "card #5")
    show("  In the console: Decisions > \"Shadow would change\" shows what the shadow rule would have stopped.")


def takeover(api, score, args):
    """
    Account takeover. Four customers use their cards as usual on their own phones. Then an
    intruder, on a device none of those cards has used, makes a large payment on each.
    NEW_DEVICE_HIGH_AMOUNT steps those up: an established card, a new device, and over Rs 20,000.
    """
    run = run_id()
    show(f"\nAccount takeover, run {run}: four customers, then an intruder on a new device.\n")
    header()
    victims = random.sample(NAMES, 4)
    for name in victims:
        for j in (1, 2):
            pay(api, score, "ordinary", f"{name} pays", f"txn-{run}-{name}-{j}", f"card-{name}-{run}",
                f"dev-{name}-{run}", everyday_rupees(), random.choice(MERCHANTS))
    show()
    for name in victims:
        pay(api, score, "attack", f"intruder on {name}'s card", f"txn-{run}-{name}-x", f"card-{name}-{run}",
            f"dev-intruder-{run}", random.randint(25_000, 45_000), "mer-cinemax")
        time.sleep(0.3)


def story(api, score, args):
    """The original scripted story: ordinary payments, one big one, then a card-testing burst."""
    run = run_id()
    show(f"\nThe story, run {run}\n")
    header()
    show("  ordinary customers")
    for i, (name, rupees) in enumerate([("asha", 450), ("ravi", 1_299), ("meera", 2_150),
                                        ("kabir", 180), ("zoya", 4_500)], start=1):
        pay(api, score, "ordinary", f"{name} pays", f"txn-{run}-n{i}", f"card-{name}-{run}",
            f"dev-{name}-{run}", rupees, random.choice(MERCHANTS))
    show("  a large purchase")
    pay(api, score, "ordinary", "priya buys a laptop", f"txn-{run}-big", f"card-priya-{run}",
        f"dev-priya-{run}", 75_000, "mer-metro-mart")
    show("  card testing: a stolen card, a script, tiny amounts")
    wait_for_quiet_moment()
    burst = []
    for i in range(1, 9):
        txn = f"txn-{run}-b{i}"
        pay(api, score, "attack", f"attempt #{i}", txn, f"card-stolen-{run}", f"dev-bot-{run}",
            random.randint(1, 99), "mer-fixit")
        burst.append(txn)
        time.sleep(0.2)
    show("\n  Why? The stored explanations (GET /v1/decisions/{id}):")
    explain(api, burst[3], "attempt #4")
    explain(api, burst[5], "attempt #6")
    show("\n  Try next:")
    show("    - the attack scenarios: normal, card-testing, enumeration, takeover (see the top of this file)")
    show("    - in the Ubuntu window: sudo service redis-server stop, then run this again. Every decision is")
    show("      degraded, the burst is no longer caught, and big payments are stepped up by DEGRADED_FALLBACK.")
    show("      sudo service redis-server start afterwards.")


SCENARIOS = {
    "story": story,
    "normal": normal,
    "card-testing": card_testing,
    "enumeration": enumeration,
    "takeover": takeover,
}


def main():
    parser = argparse.ArgumentParser(description="Send payments to Sentinel and see what it decides.")
    parser.add_argument("scenario", nargs="?", default="story", choices=SCENARIOS.keys())
    parser.add_argument("--url", default="http://localhost:8080", help="where Sentinel runs")
    parser.add_argument("--api-key", default=os.environ.get("SENTINEL_API_KEY", DEFAULT_KEY),
                        help="API key (default: SENTINEL_API_KEY, else the local dev key)")
    parser.add_argument("--rate", type=float, default=2.0, help="payments per second, for 'normal'")
    parser.add_argument("--seconds", type=float, default=0, help="stop 'normal' after this long (0 = never)")
    parser.add_argument("--quiet", action="store_true", help="print only the summary")
    args = parser.parse_args()
    if args.rate <= 0:
        parser.error("--rate must be above 0")

    api = Sentinel(args.url, args.api_key)
    if not api.reachable():
        show(f"Could not reach Sentinel at {api.base}. Is the backend running? (.\\mvnw spring-boot:run in backend)")
        sys.exit(1)

    score = Scorecard()
    try:
        SCENARIOS[args.scenario](api, score, args)
    except KeyboardInterrupt:
        show("\n  stopped")
    score.report()


if __name__ == "__main__":
    main()
