# ADR 0007: Canary rollouts are bounded device assignments

- Status: Proposed
- Date: 2026-08-20

## Context

An approved snapshot should reach a small known set of televisions before a
broader deployment. The group mechanism must not become an alternative content
publication path, weaken mosque isolation, create a group-wide mutable manifest
or make a partial batch look successful.

## Decision

Migration v4 adds one optional 8–64 character ASCII-slug `rollout_group` label
to a device.
Only an authorized mosque/service administrator can set or clear it. Labels are
operator targeting metadata, not device claims or audience analytics.

A group assignment accepts only a snapshot ID resolved through the API's
verified immutable registry. PostgreSQL rechecks the actor and mosque, selects
non-revoked members with a 101st-row overflow sentinel, locks them in
deterministic ID order, and updates at most 100 normal `device_assignment` rows
plus their audit transitions in one transaction. Empty, cross-scope and
oversized groups fail without mutation.

Every device retains its own monotonic manifest version. An exact retry within
24 hours returns the stored ordered result before registry lookup. Rollback is
the same operation with a prior verified snapshot ID, producing newer manifest
versions that point to older known-good snapshot bytes.

## Consequences

Positive:

- canary and rollback use the same signed snapshot/device read contract;
- batch failure cannot leave a partially assigned cohort;
- deterministic locks bound deadlock and response-order ambiguity;
- group labels reveal no attendee or behavioral information.

Costs:

- an operator explicitly manages membership and promotion;
- cohorts larger than 100 must be split deliberately;
- no automatic percentage rollout or health-gated promotion exists yet;
- controlled PostgreSQL evidence does not replace a physical-TV canary drill.

## Rejected alternatives

- client-selected cohorts;
- percentage/random targeting hidden from the operator;
- a mutable group manifest shared outside per-device assignments;
- accepting snapshot URL/hash/key from an admin request;
- best-effort per-device loops that can partially commit.
