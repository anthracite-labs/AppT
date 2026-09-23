/**
 * AppT Entitlement Backend — package entry point.
 *
 * S01 is a **non-deploying skeleton**. This package exists so the TypeScript,
 * lint and test toolchain is real and wired into CI from the first slice
 * (docs/architecture/release.md#dependency-set).
 *
 * Deliberately absent, and not to be added here ahead of their slices:
 *
 *  * any exported Cloud Function or HTTPS endpoint;
 *  * Firestore schema, rules or indexes;
 *  * Firebase project configuration, `firebase.json`, or environment variants;
 *  * App Check or Play Billing integration;
 *  * a deployment workflow.
 *
 * The backend never enters the Android dependency graph; the only contract
 * between the two is the future HTTPS API in docs/architecture/sync.md.
 */

export { backendPackage, type BackendPackage } from './package-identity';
