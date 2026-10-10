.DEFAULT_GOAL := help
SHELL := /bin/bash

# Optional compose profiles for "make up", e.g. make up PROFILES="ollama monitoring"
PROFILES ?=
PROFILE_FLAGS := $(foreach profile,$(PROFILES),--profile $(profile))

.PHONY: help up up-fast down restart logs ps status watch dev dev-stop dev-logs test test-backend test-frontend

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

up: ## Build and start the Docker stack (PROFILES="qwen llama ollama monitoring" optional)
	@scripts/deploy.sh up $(PROFILE_FLAGS)

up-fast: ## Start the Docker stack without rebuilding images
	@scripts/deploy.sh up --no-build $(PROFILE_FLAGS)

down: ## Stop the Docker stack (data is kept)
	@scripts/deploy.sh down

restart: ## Restart backend and frontend containers
	@scripts/deploy.sh restart

logs: ## Follow container logs
	@scripts/deploy.sh logs

ps: ## List containers
	@scripts/deploy.sh ps

status: ## Health of containers, endpoints, database and LLM providers
	@scripts/status.sh

watch: ## Refresh the status every 5 seconds
	@scripts/status.sh --watch 5

dev: ## Development mode: PostgreSQL container + backend and Vite dev server on this machine
	@scripts/run-dev.sh start

dev-stop: ## Stop development mode
	@scripts/run-dev.sh stop

dev-logs: ## Follow development logs
	@scripts/run-dev.sh logs

test: ## Run all tests (frontend, then backend)
	@scripts/test.sh all

test-backend: ## Backend unit and integration tests (Testcontainers)
	@scripts/test.sh backend

test-frontend: ## Frontend type check, lint and Vitest
	@scripts/test.sh frontend
