# Auth in 1.0

LytgOS2 1.0 has **no authentication, authorization, RBAC, SSO, API keys, or CORS whitelist**. It is a single-node runtime kernel intended for a trusted private network.

## What that means

- Every REST endpoint is unauthenticated.
- CORS is open.
- Anyone who can reach port `8989` can invoke Agents and read Sessions.
- Do not expose the service directly to the public internet.

## Recommended deployment

- Run behind a reverse proxy or VPN.
- Bind the service to a private interface, or restrict the port with a firewall/security group.
- Use network-level access control (VPC, ingress allowlist, mTLS at the proxy).
- Keep provider keys in environment variables or an external config file; never commit them.

## Extension direction

Authentication (OIDC), RBAC, API keys, CORS allowlists, multi-tenancy, and SSO are extension-phase capabilities. They are not half-implemented in 1.0.

## Related

- [REST API](/docs/api)
- [Roadmap](/docs/roadmap)
