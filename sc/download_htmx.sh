#!/bin/bash
DIR=$(dirname "$(readlink -f "$0")")
DEST="$DIR"/../src/main/resources/META-INF/resources/out/js/

echo "Downloading htmx-ext-sse"
SSE_FILE="$DIR"/sse.js
curl -L "https://unpkg.com/htmx-ext-sse" -o "$SSE_FILE"
rm -f "$DEST"/sse.js
bun build "$SSE_FILE" --minify --target=browser --outfile "$DEST"/sse.js
rm "$SSE_FILE"
#curl -L "https://unpkg.com/htmx.org/dist/ext/sse.js" -o "$SSE_FILE"

echo "Downloading htmx"
curl -L "https://unpkg.com/htmx.org@2" -o "$DEST"/htmx.js
#bun build "$HTMX_FILE" --minify --target=browser --outfile "$DEST"/htmx.js