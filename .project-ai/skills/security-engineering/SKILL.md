---
name: security-engineering
description: Use when work touches authentication, authorization, secrets, cryptography, untrusted input, uploads or parsing, network trust boundaries, privileged operations, multi-tenancy, payments, sensitive data, supply chain risk, privileged CI/CD, or an explicit security review or audit.
---

# Security Engineering

## Purpose

Apply security depth in proportion to the actual risk surface.

Security starts with trust and abuse analysis, continues through implementation hardening, and becomes evidence-based review or audit when code exists.

## Decision rules

| Mode | Use it when | Goal |
|---|---|---|
| Design | requirements or architecture expose security-sensitive behavior | identify assets, principals, trust boundaries, abuse cases, required controls |
| Hardening | implementing a security-sensitive surface | place secure controls at real boundaries |
| Review | reviewing a specific change | trace concrete exploit paths before reporting findings |
| Audit | explicitly requested or risk justifies deeper examination | systematically test trust-boundary violations and fixes |

Use the lightest mode that can answer the question safely.

## Workflow

### 1. Define scope, principals, assets, and trust boundaries

Identify:

- who can act;
- what they can affect;
- what is valuable or sensitive;
- where less-trusted data or authority crosses into more-trusted code;
- attacker capabilities relevant to the surface.

Trust follows who can influence a value, not the channel that delivered it.

### 2. Model abuse

For each material behavior, ask how it could be misused.

Consider as relevant:

- impersonation;
- authorization bypass;
- tampering;
- disclosure;
- replay or duplicate effects;
- denial of service;
- privilege escalation;
- cross-tenant access;
- unsafe paths/files;
- injection;
- supply-chain compromise.

Use threat frameworks as lenses, not ceremony.

### 3. Put controls at real boundaries

As applicable:

- authenticate principals;
- authorize the specific action on the specific resource;
- validate externally controlled data before trusted use;
- parameterize interpreters and queries;
- encode/sanitize at output boundaries;
- minimize privileges;
- constrain destructive or expensive side effects;
- protect secrets;
- cap size, rate, time, concurrency, or recursion;
- verify signatures/provenance where trust depends on them.

Do not scatter redundant validation through already trusted internal paths.

### 4. Handle sensitive data and dependencies deliberately

For sensitive data, minimize collection, restrict access, keep secrets and unnecessary personal data out of logs, and account for retention/deletion obligations where relevant.

For dependencies, identify the real package-manager/lockfile boundary, inspect advisories and provenance, consider reachability, and avoid forced broad upgrades without compatibility evidence.

A green audit tool does not prove supply-chain safety.

### 5. Review by tracing real data and authority flow

Before reporting a vulnerability, establish:

- attacker-controlled input or action;
- reachable path;
- existing validation, authorization, sanitization, or framework protection;
- affected principal/resource;
- concrete security consequence.

Classify findings:

- **CONFIRMED** — exploit path and attacker control established;
- **NEEDS VERIFICATION** — plausible material risk with unresolved exploit conditions;
- **DEFENSE IN DEPTH** — improvement without a demonstrated exploit path.

### 6. Deep-audit only when warranted

For a full audit, each material finding should include:

- attacker capability;
- crossed trust boundary;
- affected principal/resource;
- impact;
- safe reproduction or evidence;
- severity rationale;
- smallest effective fix;
- verification requirement.

Avoid reproduction that could damage users, data, or systems.

### 7. Verify corrections

Re-test the original exploit condition or security property and run relevant regression checks using the canonical verification strategy.

## Output contract

Produce only what the active mode requires:

- Security Scope
- Material Assets / Principals / Trust Boundaries
- Abuse Cases
- Required Controls
- Findings with confidence, evidence, impact, fix, and verification
- Residual Risk
- Material decisions requiring escalation

## Boundaries

- Do not run heavyweight audit ceremony on unrelated low-risk changes.
- Do not report vulnerabilities from pattern matching alone.
- Do not confuse authentication with resource-level authorization.
- Do not log secrets or sensitive payloads for debugging.
- Do not silently expand product scope to fix a material architecture or trust-boundary problem.
- Do not let a passing dependency audit substitute for dependency provenance and reachability judgment.

## Completion gate

Before handoff, confirm:

- security depth matches the risk surface;
- material trust boundaries and abuse paths are understood;
- required controls map to real boundaries;
- findings distinguish confirmed exploitation from uncertainty;
- residual risk is explicit;
- security fixes have targeted verification evidence.