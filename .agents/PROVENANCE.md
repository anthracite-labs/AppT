# Provenance

Immutable upstream provenance for vendored capabilities.

Capability selection and local skill paths are owned by `.agents/CAPABILITIES.md`. This file does not duplicate that catalog.

Each vendored upstream file must match its counterpart at the recorded upstream revision exactly. Repository-specific integration belongs outside vendored content.

## Upstream sources

### `mattpocock/skills`

Repository: `https://github.com/mattpocock/skills`

Revision: `c55ee46073ed923f86ce59a5eb3b6d895095d1b7`

License: MIT

Local license: `.agents/licenses/mattpocock-skills.LICENSE`

Upstream license: `https://github.com/mattpocock/skills/blob/c55ee46073ed923f86ce59a5eb3b6d895095d1b7/LICENSE`

### `cloudflare/security-audit-skill`

Repository: `https://github.com/cloudflare/security-audit-skill`

Revision: `c1c8a8c1471069fb0e188eeaff69b8e8db6564a8`

License: MIT

Local license: `.agents/licenses/cloudflare-security-audit-skill.LICENSE`

Upstream license: `https://github.com/cloudflare/security-audit-skill/blob/c1c8a8c1471069fb0e188eeaff69b8e8db6564a8/LICENSE`

Rationale: adopted for the dedicated deep-security-audit capability routed separately from normal security reasoning.

## Integrity

Vendored upstream files must remain byte-identical to their counterparts at the recorded revision.

Upstream copyright and license notices must be retained unchanged in `.agents/licenses/`.

Do not locally modify vendored skills to adapt them to this repository. Put repository-specific routing, policy, compatibility, or orchestration in repository-owned files.

## Updating an upstream

An upstream update is an explicit repository change.

1. Select the new exact upstream commit.
2. Review the upstream changes between the current and proposed revisions.
3. Replace affected vendored files with their counterparts from the new revision.
4. Replace the retained upstream license if it changed.
5. Verify the vendored files and retained license match the new revision.
6. Update this file in the same change.

Do not move a provenance pin independently of the vendored content it identifies.
