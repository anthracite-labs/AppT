import { backendPackage } from '../src';

describe('backend package skeleton', () => {
  it('identifies itself as the AppT backend package', () => {
    expect(backendPackage.name).toBe('appt-backend');
  });

  it('declares no deployable surface in S01', () => {
    // S01 adds no Cloud Function, HTTPS endpoint or deployment workflow.
    // This assertion is the tripwire: adding one without flipping this flag
    // (and reviewing why) fails the backend test job.
    expect(backendPackage.deployable).toBe(false);
  });
});
