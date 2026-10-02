export interface BackendPackage {
  readonly name: 'appt-backend';
  readonly deployable: false;
}

export const backendPackage: BackendPackage = {
  name: 'appt-backend',
  deployable: false,
};
