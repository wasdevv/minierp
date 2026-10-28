# Sourced by the scripts that drive the app over HTTP: logs in once and keeps the session in a cookie jar.
#   source scripts/session.sh; login "$BASE" admin "$PASSWORD"; post /products -d ...
# Spring Security masks the CSRF token per response, but any token issued for the session is valid.

JAR=${JAR:-$(mktemp)}

login() {  # base user password
    BASE=$1
    local token
    token=$(curl -sf -b "$JAR" -c "$JAR" "$BASE/login" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//; s/"$//')
    curl -sf -b "$JAR" -c "$JAR" -o /dev/null -X POST "$BASE/login" \
        --data-urlencode "username=$2" --data-urlencode "password=$3" -d "_csrf=$token"
    CSRF=$(curl -sf -b "$JAR" -c "$JAR" "$BASE/products/new" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//; s/"$//')
    [ -n "$CSRF" ] || { echo "login failed" >&2; return 1; }
}

post() {  # path curl-args... ; prints the redirect target
    curl -sf -b "$JAR" -c "$JAR" -o /dev/null -w '%{redirect_url}\n' -X POST "$BASE$1" -d "_csrf=$CSRF" "${@:2}"
}

page() {  # path ; prints the HTML
    curl -sf -b "$JAR" -c "$JAR" "$BASE$1"
}
