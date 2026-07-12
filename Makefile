CRUNNER_IMAGE ?= cristiandelahooz/socratic-tutor-crunner:latest
CRUNNER_PLATFORMS ?= linux/amd64,linux/arm64

.PHONY: build-crunner
build-crunner:
	docker buildx build --platform $(CRUNNER_PLATFORMS) --tag $(CRUNNER_IMAGE) --push ops/docker/c-runner
