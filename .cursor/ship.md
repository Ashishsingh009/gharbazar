# Ghar Bazaar Shipping Contract

Status: Draft release and CI/CD contract  
Version: 1.1

## 1. Branching and pull requests

- Use short-lived feature branches.
- Every pull request references requirement, specification, and task IDs.
- Product behavior changes update `intent.md` before implementation.
- Architecture changes update `spec.md` and, when appropriate, an ADR.
- A pull request cannot merge with failing contract, build, or validation checks.

## 2. CI stages

```text
Markdown and ID lint
  -> Traceability check
  -> JSON/schema validation
  -> API contract validation
  -> Android lint and unit tests
  -> Backend tests
  -> A2UI fixture tests
  -> Integration tests
  -> Security scan
  -> Build artifacts
  -> Smoke tests
```

## 3. Required release gates

### REL-001 Traceability

Every `FR-*`, `NFR-*`, and `A2UI-*` requirement has a specification, implementation task, and validation reference. `tools/traceability.py` enforces this check, and `.github/workflows/ci.yml` runs it before tests.

### REL-002 Contract validity

The A2UI catalog, API schema, tool contracts, and localization schema validate successfully.

### REL-003 Test health

Critical unit, contract, integration, offline, security, and end-to-end tests pass.

### REL-004 Android artifact

The signed Android build is reproducible and launches the vertical slice successfully.

### REL-005 Backend artifact

The Cloud Run image is built, scanned, deployed to the target environment, and passes smoke tests.

### REL-006 Observability

Crash reporting, agent latency, A2UI rejection, tool failure, and synchronization metrics are available before rollout.

### REL-007 Nutrition correctness

Recipe ingredient and nutrition flows must pass recipe completeness, serving-size, protein-estimate, transparency, and medical-safety validation before being enabled in production.

## 4. Environments

```text
local       Developer fixtures and emulators
staging     Test Cloud Run service and Firestore project
production  Controlled rollout with feature flags
```

Secrets must be supplied through the deployment environment or secret manager. They must never be committed to Markdown, fixtures, source code, or CI logs.

## 5. Deployment policy

- Deploy backend before enabling a client feature that depends on it.
- Keep backward-compatible A2UI catalog versions during client rollout.
- Use feature flags for agent-backed flows.
- Deploy database/index changes before code that requires them.
- Run smoke tests after every staging and production deployment.

## 6. Rollback

Rollback must support:

- Reverting the Android feature flag.
- Re-deploying the previous Cloud Run image.
- Disabling a catalog version while retaining the previous compatible version.
- Replaying or safely abandoning failed synchronization operations.
- Preserving user-created local grocery data.

## 7. Current automated evidence

`/.github/workflows/ci.yml` runs traceability, the secret scan, backend contract tests, the health smoke check, `:domain:test`, and `:app:assembleDebug`.

Still open before a production rollout:

- REL-004 signed release artifact.
- REL-005 Cloud Run deploy. The service is fixture-tested locally. Gemini and Firestore stay behind `GEMINI_API_KEY` and `FIRESTORE_PROJECT`.
- REL-006 vendor crash-reporting dashboards. The client records privacy-safe in-process metrics.
- Account linking stays on `UnconfiguredAccountLink` until an identity provider is chosen.

## 8. Release checklist

- [ ] All changed requirements are identified.
- [ ] `intent.md` and `spec.md` are synchronized.
- [ ] Implementation tasks are complete.
- [ ] Validation matrix has evidence.
- [ ] A2UI catalog compatibility is reviewed.
- [ ] Security and privacy checks pass.
- [ ] Localization checks pass.
- [ ] Android artifact is signed.
- [ ] Backend deployment succeeds.
- [ ] Smoke tests pass.
- [ ] Monitoring dashboards and alerts are active.
- [ ] Rollback procedure is known.
