# Expense Tracker

A lightweight browser-based expense tracker that stores data in `localStorage`, with optional cloud sync via the MasterAuth API.

## Features

- Add, edit, and delete expenses
- Filter expenses by pay cycle (based on configured pay day)
- Monthly summary cards:
  - Monthly Income
  - Total Expenses
  - Remaining Balance
- Configurable Pay Day (`1-31`), defaulting to day `1` when unset
- Category summary chart (Chart.js)
- Backup and restore data as JSON
- Optional cloud account:
  - Register / login
  - Manual sync
  - Background sync while signed in

## Tech Stack

- HTML + CSS + JavaScript (no build step)
- Bootstrap 5
- Bootstrap Icons
- jQuery
- Select2
- SweetAlert2
- Chart.js

## Project Structure

- `index.html` - Main UI and modals
- `apps.js` - App logic, storage, auth, sync, rendering
- `styles.css` - Custom styles
- `serve-https.sh` - Local HTTPS static server with self-signed cert
- `version.sh` - Bumps asset query version in `index.html`
- `repo.sh` - Helper for add/commit/push
- `masterAuth.md` - API behavior and endpoint notes
- `.asset-version` - Current asset cache-busting version
- `.cert/` - Generated local TLS certificate and key

## Requirements

- macOS/Linux shell (bash)
- `python3`
- `openssl`
- Internet access for CDN dependencies and API calls

## Quick Start

1. Make scripts executable (first time only):

```bash
chmod +x serve-https.sh version.sh repo.sh
```

2. Start local HTTPS server:

```bash
./serve-https.sh
```

3. Open in browser:

- `https://localhost:8080`

> The first run generates a self-signed certificate in `.cert/`.

## Data Storage

Local keys used by the app include:

- `expenses`
- `monthlyIncome`
- `payDay`
- `masterauth_profile_v1`
- `masterauth_last_sync_v1`
- Cookie: `masterauth_password_key`

## Cloud Sync Notes

- API base URL in `apps.js`:
  - `https://api.brandon.my/v1/api`
- Auth + sync flow is documented in `masterAuth.md`.
- Login stores `password_key` in cookie and enables sync.
- App decides whether to pull from cloud or push local data based on sync timestamp and data presence.

## Utility Scripts

### `repo.sh`

Commit and push quickly:

```bash
./repo.sh "Update"
./repo.sh
```

- Default commit message: `Update`

### `version.sh`

Bump asset version query values in `index.html`:

```bash
./version.sh
```

Useful when you want to force browsers to fetch updated `styles.css` and `apps.js`.

## Notes

- This is a static front-end app. No Node.js install or build command is required.
- If cloud API is unavailable, local tracking still works.
