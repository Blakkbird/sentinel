# The traffic simulator, so you can send test payments without installing Python.
#
# It uses nothing but Python's standard library, so there is nothing to install here
# either: the image is Python plus one script.
#
# docker compose builds this only when you ask for it (the "sim" profile):
#
#   docker compose --profile sim run --rm simulator               the scripted story
#   docker compose --profile sim run --rm simulator normal        ordinary traffic
#   docker compose --profile sim run --rm simulator card-testing  an attack

FROM python:3.13-alpine
WORKDIR /app

RUN adduser -S sentinel
COPY traffic.py ./
USER sentinel

# --url points at the backend container rather than localhost, which inside a container
# would mean the simulator itself. Anything you type after "run --rm simulator" is added
# to this line, so a scenario and options can still be passed:
#   docker compose --profile sim run --rm simulator normal --rate 5
# -u: print each decision as it happens instead of in blocks when the buffer fills.
ENTRYPOINT ["python", "-u", "traffic.py", "--url", "http://backend:8080"]
