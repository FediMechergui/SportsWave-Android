"""Bounded, explicit Apify import. Never embed an account token in the Android app."""
import argparse
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.apify.com/v2/"
ACTOR = "azzouzana~sofascore-scraper-pro"
TERMINAL = {"SUCCEEDED", "FAILED", "ABORTED", "TIMED-OUT"}


def validate_items(items):
    if not isinstance(items, list) or not items:
        raise ValueError("The dataset contains no records; the previous export was retained.")
    if any(not isinstance(item, dict) for item in items):
        raise ValueError("Unsupported dataset record format.")
    failures = [item for item in items if item.get("error")]
    if failures:
        reason = "NO ND script found" if any("NO ND script found" in str(x["error"]) for x in failures) else "scraper error"
        raise ValueError(f"{len(failures)}/{len(items)} records contain {reason}; no usable feed exported.")
    return items


def write_export(path, items):
    validate_items(items)
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(items, ensure_ascii=False), encoding="utf-8")
    temporary.replace(path)


class Apify:
    def __init__(self, token):
        self.token = token

    def request(self, path, body=None):
        request = urllib.request.Request(API + path,
            data=None if body is None else json.dumps(body).encode(),
            headers={"Authorization": "Bearer " + self.token, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                data = response.read(8_000_001)
                if len(data) > 8_000_000:
                    raise ValueError("Dataset too large; request fewer pages.")
                return json.loads(data)
        except urllib.error.HTTPError as error:
            # Do not expose request headers, raw response bodies or token-bearing URLs.
            raise ValueError(f"Apify HTTP {error.code}; check token, actor availability and account limits.") from None

    def run(self, urls, charge, timeout):
        for url in urls:
            parsed = urllib.parse.urlsplit(url)
            if parsed.scheme != "https" or parsed.hostname != "www.sofascore.com" or parsed.username or parsed.password or parsed.port not in (None, 443):
                raise ValueError("Only public HTTPS www.sofascore.com pages are accepted.")
        query = urllib.parse.urlencode({"timeout": timeout, "memory": 1024,
            "maxTotalChargeUsd": charge, "maxItems": len(urls)})
        run = self.request(f"acts/{ACTOR}/runs?{query}", {"startUrls": urls})["data"]
        deadline = time.monotonic() + timeout + 45
        while run["status"] not in TERMINAL:
            if time.monotonic() > deadline:
                self.request("actor-runs/" + run["id"] + "/abort", {})
                raise ValueError("Run exceeded the local deadline and was aborted.")
            time.sleep(3)
            run = self.request("actor-runs/" + run["id"])["data"]
        if run["status"] != "SUCCEEDED":
            raise ValueError("Actor ended with " + run["status"] + "; previous export retained.")
        print("Actor usage: $" + str(round(run.get("usageTotalUsd", 0), 6)))
        return self.request("datasets/" + run["defaultDatasetId"] + "/items?clean=true&limit=1000")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", action="append", default=[])
    parser.add_argument("--token-file", type=Path)
    parser.add_argument("--input-file", type=Path, help="Validate a local export without network or spending")
    parser.add_argument("--output", type=Path, default=Path("audit/apify-export.json"))
    parser.add_argument("--max-charge", type=float, default=0.10)
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()
    try:
        if args.input_file:
            items = json.loads(args.input_file.read_text(encoding="utf-8"))
        else:
            if not 1 <= len(args.url) <= 3 or not 0 < args.max_charge <= 1 or not 10 <= args.timeout <= 120:
                raise ValueError("Use 1–3 URLs, a charge cap of at most $1 and a timeout of 10–120 seconds.")
            token = args.token_file.read_text(encoding="utf-8").strip() if args.token_file else os.environ.get("APIFY_TOKEN", "")
            if not token:
                raise ValueError("Set APIFY_TOKEN or provide a private --token-file outside the repository.")
            items = Apify(token).run(args.url, args.max_charge, args.timeout)
        write_export(args.output, items)
        print(f"Exported {len(items)} records for schema review. This raw dataset is not an Android score feed.")
        return 0
    except (ValueError, OSError, TimeoutError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
