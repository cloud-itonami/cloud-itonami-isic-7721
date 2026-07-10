# Security Policy

This project handles safety-inspection-scope, waiver-scope and
reconciliation-record workflows. Treat vulnerabilities as potentially
high impact even when the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- safety-inspection/waiver credential exposure
- real renter or payment data exposure
- authorization bypass
- Recreational Rental Governor bypass
- audit-ledger tampering
- over-disclosure in reconciliation records or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on renter/payment data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real renter and payment data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
