# Day-to-day commands. Integration tests need MINIERP_TEST_JDBC_URL (see README, Testes).
.PHONY: help test verify run up down logs image scan-image browser-check screenshots

help:            ## list the targets
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-15s %s\n", $$1, $$2}'

test:            ## unit + integration tests
	./mvnw -B test

verify:          ## tests + coverage gate (what CI runs)
	./mvnw -B verify

run:             ## app on :8080 against DB_URL / MINIERP_ADMIN_PASSWORD from the environment
	./mvnw -B spring-boot:run

up:              ## PostgreSQL + app in containers, waits until healthy
	docker compose up -d --build --wait

down:            ## stop and remove containers and volumes
	docker compose down -v

logs:            ## follow the app logs
	docker compose logs -f app

image:           ## build the production image locally
	docker build -t mini-erp:local .

scan-image: image ## Trivy scan of the local image (HIGH/CRITICAL with a fix)
	trivy image --severity HIGH,CRITICAL --ignore-unfixed --ignorefile .trivyignore mini-erp:local

browser-check:   ## real Chrome on every page: CSP, JS errors (needs the app running and Node 22)
	node scripts/browser-check.mjs http://localhost:8080 admin $${MINIERP_ADMIN_PASSWORD:-admin_local}

screenshots:     ## regenerate docs/screenshots (see the script header for variables)
	scripts/screenshots.sh
