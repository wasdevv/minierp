# Sourced by the scripts that drive the app over HTTP: logs in once and keeps the session in a cookie jar.
#   source scripts/session.sh; login "$BASE" admin "$PASSWORD"; post /products -d ...
# Spring Security masks the CSRF token per response, but any token issued for the session is valid.
# Callers run under `set -o pipefail`: never pipe curl into something that stops reading early (head, grep -q),
# or curl fails with exit 23 once the page outgrows the pipe buffer. Read the whole response first.

JAR=${JAR:-$(mktemp)}

csrf_in() {  # html ; prints the first CSRF token in it
    local html=$1
    [[ $html == *'name="_csrf" value="'* ]] || return 0
    html=${html#*name=\"_csrf\" value=\"}
    printf '%s' "${html%%\"*}"
}

login() {  # base user password
    BASE=$1
    local token
    token=$(csrf_in "$(curl -sf -b "$JAR" -c "$JAR" "$BASE/login")")
    curl -sf -b "$JAR" -c "$JAR" -o /dev/null -X POST "$BASE/login" \
        --data-urlencode "username=$2" --data-urlencode "password=$3" -d "_csrf=$token"
    CSRF=$(csrf_in "$(curl -sf -b "$JAR" -c "$JAR" "$BASE/products/new")")
    [ -n "$CSRF" ] || { echo "login failed" >&2; return 1; }
}

post() {  # path curl-args... ; prints the redirect target
    curl -sf -b "$JAR" -c "$JAR" -o /dev/null -w '%{redirect_url}\n' -X POST "$BASE$1" -d "_csrf=$CSRF" "${@:2}"
}

page() {  # path ; prints the HTML
    curl -sf -b "$JAR" -c "$JAR" "$BASE$1"
}
