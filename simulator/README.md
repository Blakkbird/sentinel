# Simulator

`traffic.py` sends a short, scripted story of payments to a running Sentinel and prints what it
decided: five ordinary payments, one large payment, and a card-testing burst on a stolen card.

```
python simulator/traffic.py                   # app on http://localhost:8080
python simulator/traffic.py --url http://...  # somewhere else
```

It uses only Python's standard library, so there is nothing to install. It is a demo and a smoke
test for now; growing it into a fuller traffic generator with labelled attack scenarios is part of
the plan we will work out together.
