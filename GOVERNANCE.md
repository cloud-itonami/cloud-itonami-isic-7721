# Governance

`cloud-itonami-7721` is an OSS open-business blueprint for community
recreational and sports goods rental operations, robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Recreational Rental Governor remains independent of the
  advisor.
- hard policy violations (an equipment release outside verified
  safety-inspection scope, an unverified waiver record, an
  unverified reconciliation record) cannot be overridden by human
  approval.
- every dispatch, sign-off and reconciliation path is auditable.
- sensitive renter and payment data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require security, robot-safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing robot-safety or safety-inspection checks
- mishandling renter or payment data
- misrepresenting certification status
- failing to respond to safety incidents
