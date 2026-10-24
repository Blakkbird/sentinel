# Simulator

`traffic.py` sends payments to a running Sentinel the way a payment system would (with an API key),
prints what it decided, and ends with a scorecard that separates ordinary payments from attacks.

| Command (Windows: `py simulator\traffic.py ...`) | What it sends | What Sentinel should do |
|---|---|---|
| `python simulator/traffic.py` | The scripted story: ordinary payments, a laptop, a card-testing burst | Approve, step up the laptop, decline the burst from attempt 6 |
| `... normal` | Ordinary customers, 2 a second, until Ctrl+C (`--rate`, `--seconds`) | Approve almost all; step up the odd big purchase |
| `... card-testing` | One stolen card tried ten times with tiny amounts | Decline from attempt 6 (`VEL_CARD_1M`); the shadow rule would from attempt 4 |
| `... enumeration` | One bot device, 25 different stolen cards | Approve, but the shadow rule `DEVICE_MANY_CARDS_1H` would decline from card 5; step up from payment 21 |
| `... takeover` | Customers on their own phones, then an intruder on a new device | Step up the intruder's large payments (`NEW_DEVICE_HIGH_AMOUNT`) |

Options: `--url` (default `http://localhost:8080`), `--api-key` (default: `SENTINEL_API_KEY`, else the
local dev key), `--quiet` (summary only).

**For a demo:** run `normal` in one terminal with the console's Live page open, then start an attack
from a second terminal. The attack appears in the live tape as it happens.

It uses only Python's standard library, so there is nothing to install.
