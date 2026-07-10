# cloud-itonami-7721

Open Business Blueprint for **ISIC Rev.5 7721**: renting and leasing
of recreational and sports goods (skis, bicycles, watersports and
camping equipment, and similar recreational gear).

This repository designs a forkable OSS business for community
recreational-goods rental: equipment-safety-inspection and waiver-
scope management, robotics-assisted equipment-condition inspection at
checkout/return, and rental/reconciliation records — run by a
qualified operator so a rental shop keeps its own inspection and
consumer-protection compliance history instead of renting a closed
equipment-management platform.

## Scope note: recreational gear, not vehicles or construction tools

Distinct from `cloud-itonami-isic-7710` ("Community Vehicle Rental
Operations", motor vehicles) and `cloud-itonami-unspsc-27`
("Independent Tool Fleet Rental & Maintenance Robotics", construction
tools and equipment): this repository is deliberately scoped to
recreational and sports equipment -- skis, bicycles, kayaks, camping
gear and similar goods rented to individual consumers for personal
recreational use. Recreational-equipment rental carries its own
distinct compliance concerns: safety-critical gear (ski bindings,
climbing and watersports equipment) is subject to product-specific
safety standards (e.g. ASTM/DIN ski-binding standards, CPSC bicycle
safety standards); many jurisdictions have recreational-activity
liability statutes that specifically address equipment-rental waivers
and assumption-of-risk disclosures (several US states have ski-
industry-specific liability limitation laws).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a
**robot performs the physical domain work**. Here robots (equipment-
condition inspection at checkout/return, safety-critical-component
verification such as ski-binding release-force checks) operate under
an actor that proposes actions and an independent **Recreational
Rental Governor** that gates them. The governor never releases
equipment for rental itself; `:high`/`:safety-critical` actions (an
equipment release outside verified safety-inspection scope, a waiver
record without a completed disclosure check, a reconciliation record
without verified evidence) require human sign-off.

## Core Contract

```text
intake + identity + safety-inspection/waiver scope + rental request
        |
        v
Recreational Rental Advisor -> Recreational Rental Governor -> match, rental record, or human approval
        |
        v
robot actions (gated) + inspection record + reconciliation record + audit ledger
```

No automated advice can release equipment the governor refuses, match
a renter to unsafe/out-of-scope equipment, or publish a
reconciliation record without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `7721`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/labor`](https://github.com/kotoba-lang/labor) — staff registration, dispatch, timesheet/follow-up contracts

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
