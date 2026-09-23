/**
 * The package's own identity.
 *
 * This is the smallest thing that makes the toolchain real: it is typechecked,
 * linted and tested, and it carries no product behaviour, so no later-slice
 * semantics are smuggled into the skeleton.
 *
 * `deployable` is deliberately a literal `false`. Turning it true is a visible,
 * reviewable change in the slice that actually adds an endpoint.
 */
export interface BackendPackage {
  readonly name: 'appt-backend';
  readonly deployable: false;
}

export const backendPackage: BackendPackage = {
  name: 'appt-backend',
  deployable: false,
};
