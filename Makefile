CRUNNER_IMAGE ?= cristiandelahooz/socratic-tutor-crunner:latest
CRUNNER_PLATFORMS ?= linux/amd64,linux/arm64

.PHONY: build-crunner run-production run-production-docker
build-crunner:
	docker buildx build --platform $(CRUNNER_PLATFORMS) --tag $(CRUNNER_IMAGE) --push ops/docker/c-runner

run-production:
	mvn clean package -DskipTests
	docker compose down -v
	docker compose up -d postgres
	SPRING_PROFILES_ACTIVE=prod java -jar target/socratic-tutor-*.jar

run-production-docker:
	docker compose down -v
	docker compose --profile prod up --build
