/**
 * Jest configuration for the AppT Entitlement Backend package.
 *
 * S01 wires the unit-test runner only. `firebase-functions-test` and the
 * Firestore/Functions emulator suite (`npm run test:emulator --prefix backend`)
 * arrive with the Firebase project configuration in S07 and the endpoints that
 * need them; declaring them now would put an unused Firebase toolchain and a
 * deploy-capable CLI into a slice that deploys nothing.
 */
const path = require('path');

/** @type {import('ts-jest').JestConfigWithTsJest} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/src', '<rootDir>/test'],
  testMatch: ['**/*.test.ts'],
  clearMocks: true,
  transform: {
    '^.+\\.ts$': ['<rootDir>/ts-jest-transformer.js', { tsconfig: '<rootDir>/tsconfig.test.json' }],
  },
  coverageDirectory: '<rootDir>/coverage',
  coverageReporters: ['text', ['lcov', { projectRoot: path.resolve(__dirname, '..') }]],
  collectCoverageFrom: ['src/**/*.ts', '!src/**/*.d.ts'],
  coverageThreshold: {
    global: {
      branches: 100,
      functions: 100,
      lines: 100,
      statements: 100,
    },
  },
};
