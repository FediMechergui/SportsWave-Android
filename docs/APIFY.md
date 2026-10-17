# Apify checks and safe import

Checked 14 October 2026. No account token is included in the repository, assets, APK or exported diagnostics. No recurring paid tasks were created and no account plan was changed.

## Results

The requested [azzouzana/sofascore-scraper-pro](https://apify.com/azzouzana/sofascore-scraper-pro) was tested against football, basketball and the exact match URL in its documentation. Two runs were capped at $0.10 and 120 seconds each. Both had the Apify status `SUCCEEDED`, but every dataset row contained `NO ND script found`. No sports records were returned. Combined reported usage: approximately $0.000326.

One small [abotapi/sofascore-scraper](https://apify.com/abotapi/sofascore-scraper) compatibility check requested five football fixtures, without enrichments, with a $0.02 cap and 60-second timeout. It ended `ABORTED`; its final message reported that this account did not have the proxy access required by that actor. Recorded usage: $0. No results were imported. Its documentation says a paid plan with proxy access is required; no upgrade was attempted.

**These services are not active data feeds in SportsWave.** An API token authenticates the account; it does not fix a broken scraper, grant every paid feature, or establish comprehensive live coverage.

## Explicit command, no automatic spending

The standard-library importer keeps authentication on the machine/server running it. Set `APIFY_TOKEN` in that private environment, or pass `--token-file` pointing outside the repository. Never pass the token as a command-line argument or put it in an Android asset.

```powershell
python tools/apify_import.py --url https://www.sofascore.com/football --max-charge 0.10 --timeout 120
```

Runs start only when explicitly executed. The default output is ignored `audit/apify-export.json`. Actor status alone is insufficient: empty datasets and records with errors are rejected, and an existing usable export is retained. An exported raw dataset still requires schema review and a verified normalizer before it becomes an Android score feed. No guessed private endpoints or token-bearing feed URLs are constructed.

Validate an existing export with no network or charges:

```powershell
python tools/apify_import.py --input-file path/to/export.json
python -m unittest discover -s tools -p 'test_*.py' -v
```

The first actor's successful data schema could not be observed, so no fictional event adapter or demonstration fixtures are presented as working integration. Future production refresh should happen on a backend with caching and a fixed budget, exposing only normalized public sports records to Android.
