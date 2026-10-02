const path = require('path');

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
