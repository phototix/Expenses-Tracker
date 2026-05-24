#!/usr/bin/env bash

set -euo pipefail

commit_message="${*:-Update}"

git add .
git commit -m "$commit_message"
git push
