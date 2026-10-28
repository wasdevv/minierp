#!/usr/bin/env bash
# Regenerates docs/screenshots/ from a running app over a throwaway database with fictitious data.
#
#   CHROME=/path/to/chrome PG_URL=jdbc:postgresql://localhost:5432 PG_USER=postgres scripts/screenshots.sh
#
# Needs: a JDK 21, curl, psql, a Chrome/Chromium binary, and a PostgreSQL where PG_USER may create databases.
# The database mini_erp_screenshots is dropped and recreated on every run.
set -euo pipefail

cd "$(dirname "$0")/.."
: "${CHROME:?set CHROME to a Chrome/Chromium binary}"
PG_URL=${PG_URL:-jdbc:postgresql://localhost:5432}
PG_USER=${PG_USER:-postgres}
PORT=${PORT:-8097}
DB=mini_erp_screenshots
OUT=docs/screenshots
BASE=http://localhost:$PORT
WORK=$(mktemp -d)
trap 'kill "$APP_PID" 2>/dev/null || true; rm -rf "$WORK"' EXIT

pg_host_port=${PG_URL#jdbc:postgresql://}
psql_args=(-h "${pg_host_port%%:*}" -p "${pg_host_port##*:}" -U "$PG_USER" -q)
psql "${psql_args[@]}" -c "DROP DATABASE IF EXISTS $DB" -c "CREATE DATABASE $DB"

./mvnw -B -q package -DskipTests
DB_URL="$PG_URL/$DB" DB_USERNAME="$PG_USER" DB_PASSWORD="${PG_PASSWORD:-}" SERVER_PORT=$PORT \
    java -jar target/mini-erp-*.jar > "$WORK/app.log" 2>&1 &
APP_PID=$!
for _ in $(seq 60); do curl -sf -o /dev/null "$BASE/products" && break; sleep 2; done

post() { curl -sf -o /dev/null -X POST "$BASE$1" "${@:2}"; }

# Fictitious catalog and customers; documents are check-digit test numbers, not real people.
while IFS='|' read -r name sku price stock; do
    post /products --data-urlencode "name=$name" -d "sku=$sku&price=$price&stockQuantity=$stock&active=true"
done <<'DATA'
Caneta esferográfica azul|CN-AZ|2.50|240
Caderno universitário 96 folhas|CD-96|14.90|60
Lápis grafite HB|LP-HB|1.20|500
Mochila escolar|MC-01|129.00|12
Borracha branca|BR-01|0.90|300
Marca-texto amarelo|MT-AM|4.75|8
Régua 30 cm|RG-30|3.40|90
Estojo duplo|ES-02|39.90|25
DATA
post /products --data-urlencode "name=Agenda 2025" -d "sku=AG-25&price=24.90&stockQuantity=0&_active=on"

while IFS='|' read -r name email document; do
    post /customers --data-urlencode "name=$name" --data-urlencode "email=$email" --data-urlencode "document=$document"
done <<'DATA'
Maria Souza|maria.souza@exemplo.com|529.982.247-25
João Lima|joao.lima@exemplo.com|111.444.777-35
Papelaria Exemplo Ltda|compras@papelaria.exemplo|11.222.333/0001-81
Ana Ribeiro|ana.ribeiro@exemplo.com|123.456.789-09
Colégio Modelo|financeiro@colegio.exemplo|
DATA

# customer id|product id:quantity ...; customer 0 is a walk-in sale.
while read -r customer lines; do
    args=(); i=0
    [ "$customer" != 0 ] && args+=(-d "customerId=$customer")
    for line in $lines; do
        args+=(-d "items[$i].productId=${line%%:*}" -d "items[$i].quantity=${line##*:}"); i=$((i + 1))
    done
    post /sales "${args[@]}"
done <<'DATA'
1 1:10 2:2
3 4:1 3:24 5:12
0 1:2
5 2:30 7:30 3:60
2 8:1 6:2
4 1:4 2:1 5:3
0 6:4
DATA

shot() {  # path file width height
    "$CHROME" --headless --no-sandbox --hide-scrollbars --force-device-scale-factor=1 \
        --window-size="$3,$4" --screenshot="$OUT/$2" "$BASE$1" > /dev/null 2>&1
}
today=$(date +%F)
month=$(date +%Y-%m-01)
shot /products products.png 1280 860
shot /customers customers.png 1280 720
shot /sales sales.png 1280 860
shot /sales/2 sale-detail.png 1280 720
# The filled sale form only exists as a POST response: save it and point its relative URLs at the app.
curl -sf -X POST "$BASE/sales" -d "customerId=1&addItem=1" \
    -d "items[0].productId=2&items[0].quantity=3" -d "items[1].productId=1&items[1].quantity=10" \
    | sed "s|<head>|<head><base href=\"$BASE/\">|" > "$WORK/sale-form.html"
shot_file() {  # html file width height
    "$CHROME" --headless --no-sandbox --hide-scrollbars --force-device-scale-factor=1 \
        --window-size="$3,$4" --screenshot="$OUT/$2" "file://$1" > /dev/null 2>&1
}
shot_file "$WORK/sale-form.html" sale-form.png 1280 980
shot "/reports/sales?from=$month&to=$today" sales-report.png 1280 900
shot /products products-mobile.png 390 1100

curl -sf -o "$WORK/report.pdf" "$BASE/reports/sales.pdf?from=$month&to=$today"
./mvnw -B -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile="$WORK/cp.txt"
java -cp "$(cat "$WORK/cp.txt")" scripts/RenderPdf.java "$WORK/report.pdf" "$OUT/sales-report-pdf.png" 2> /dev/null

ls -1 "$OUT"
