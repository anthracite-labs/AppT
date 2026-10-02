import { backendPackage } from '../src';

describe('backend package skeleton', () => {
  it('identifies itself as the AppT backend package', () => {
    expect(backendPackage.name).toBe('appt-backend');
  });

  it('declares no deployable surface in S01', () => {
    expect(backendPackage.deployable).toBe(false);
  });
});
