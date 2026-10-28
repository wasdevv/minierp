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
ADMIN_PASSWORD=screenshots-only
DB_URL="$PG_URL/$DB" DB_USERNAME="$PG_USER" DB_PASSWORD="${PG_PASSWORD:-}" SERVER_PORT=$PORT \
    MINIERP_ADMIN_PASSWORD=$ADMIN_PASSWORD \
    java -jar target/mini-erp-*.jar > "$WORK/app.log" 2>&1 &
APP_PID=$!
for _ in $(seq 60); do curl -sf -o /dev/null "$BASE/login" && break; sleep 2; done

JAR="$WORK/cookies"
source scripts/session.sh
login "$BASE" admin "$ADMIN_PASSWORD"

# Fictitious catalog and customers; documents are check-digit test numbers, not real people.
while IFS='|' read -r name sku price stock; do
    post /products --data-urlencode "name=$name" -d "sku=$sku&price=$price&stockQuantity=$stock&active=true" > /dev/null
done <<'DATA'
Caneta esferográfica azul|CN-AZ|2.50|400
Caderno universitário 96 folhas|CD-96|14.90|160
Lápis grafite HB|LP-HB|1.20|700
Mochila escolar|MC-01|129.00|20
Borracha branca|BR-01|0.90|400
Marca-texto amarelo|MT-AM|4.75|30
Régua 30 cm|RG-30|3.40|140
Estojo duplo|ES-02|39.90|40
DATA
post /products --data-urlencode "name=Agenda 2025" -d "sku=AG-25&price=24.90&stockQuantity=0&_active=on" > /dev/null

while IFS='|' read -r name email document; do
    post /customers --data-urlencode "name=$name" --data-urlencode "email=$email" --data-urlencode "document=$document" > /dev/null
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
    post /sales "${args[@]}" | grep -q '/sales/[0-9]' || { echo "sale rejected: $customer $lines" >&2; exit 1; }
done <<'DATA'
1 1:10 2:2
3 4:1 3:24 5:12
0 1:2
5 2:30 7:30 3:60
2 8:1 6:2
4 1:4 2:1 5:3
0 6:4
1 2:3 1:6
0 3:10 5:4
3 1:40 2:12 7:10
4 8:2
0 1:3 6:2
2 4:1 2:4
5 2:40 3:80 1:50
0 1:1
1 7:2 5:2
3 6:6 3:30
0 2:1
4 4:2 8:1
0 6:12
2 1:5 2:2 3:8
DATA

# Spread the sales over the last 14 days so the dashboard chart has a shape; sale 3 stays today.
psql "${psql_args[@]}" -d "$DB" -c "UPDATE sales SET sale_date = sale_date - ((id * 5) % 14) * interval '1 day' - (id % 5) * interval '2 hours' WHERE id <> 3"
post /sales/6/cancel --data-urlencode "reason=Cliente desistiu da compra" > /dev/null

# Every POST above left a "saved" notice in the session for the page it redirects to; consume them.
# Spring hands out one notice per visit, and a sale can hold two (registered, then cancelled).
for path in /products /customers $(seq -f '/sales/%g' 1 21) $(seq -f '/sales/%g' 1 21); do page "$path" > /dev/null; done

# Pages need the session, which Chrome does not have: save each page's HTML with curl and render the file,
# with a <base> so that the stylesheet still comes from the app (static files are public).
shot() {  # path file width height
    page "$1" | sed "s|<head>|<head><base href=\"$BASE/\">|" > "$WORK/page.html"
    "$CHROME" --headless --no-sandbox --hide-scrollbars --force-device-scale-factor=1 --virtual-time-budget=3000 \
        --window-size="$3,$4" --screenshot="$OUT/$2" "file://$WORK/page.html" > /dev/null 2>&1
}
today=$(date +%F)
month=$(date +%Y-%m-01)
shot / dashboard.png 1280 1180
shot / dashboard-mobile.png 390 1700
shot /products products.png 1280 860
shot "/products?filter=low-stock" products-low-stock.png 1280 560
shot /sales/6 sale-cancelled.png 1280 640
shot /customers customers.png 1280 720
shot /sales sales.png 1280 1000
shot /sales/2 sale-detail.png 1280 720
# The filled sale form only exists as a POST response.
curl -sf -b "$JAR" -X POST "$BASE/sales" -d "_csrf=$CSRF&customerId=1&addItem=1" \
    -d "items[0].productId=2&items[0].quantity=3" -d "items[1].productId=1&items[1].quantity=10" \
    | sed "s|<head>|<head><base href=\"$BASE/\">|" > "$WORK/sale-form.html"
"$CHROME" --headless --no-sandbox --hide-scrollbars --force-device-scale-factor=1 --virtual-time-budget=3000 \
    --window-size=1280,980 --screenshot="$OUT/sale-form.png" "file://$WORK/sale-form.html" > /dev/null 2>&1
shot "/reports/sales?from=$month&to=$today" sales-report.png 1280 900
shot /products products-mobile.png 390 1100
curl -sf "$BASE/login" | sed "s|<head>|<head><base href=\"$BASE/\">|" > "$WORK/login.html"
"$CHROME" --headless --no-sandbox --hide-scrollbars --force-device-scale-factor=1 --virtual-time-budget=3000 \
    --window-size=1280,720 --screenshot="$OUT/login.png" "file://$WORK/login.html" > /dev/null 2>&1

curl -sf -b "$JAR" -o "$WORK/report.pdf" "$BASE/reports/sales.pdf?from=$month&to=$today"
./mvnw -B -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile="$WORK/cp.txt"
java -cp "$(cat "$WORK/cp.txt")" scripts/RenderPdf.java "$WORK/report.pdf" "$OUT/sales-report-pdf.png" 2> /dev/null

ls -1 "$OUT"
