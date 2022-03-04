#!/usr/bin/env bash
set -euo pipefail

echo "⚙️ Building frontend..."
cd frontend
npm --quiet --silent run build
echo "✅ Successfully built frontend"

echo "⚙️ Building backend..."
cd ..
mvn -q clean verify
echo "✅ Successfully built backend"

echo "✅ Done"