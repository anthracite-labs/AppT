// Flat ESLint configuration for the AppT Entitlement Backend package.
//
// Typed linting is scoped to the TypeScript sources and tests; the Node config
// files in this directory are linted untyped, because they are not part of the
// TypeScript project.
import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['lib/**', 'node_modules/**', 'coverage/**'] },

  {
    files: ['src/**/*.ts', 'test/**/*.ts'],
    extends: [js.configs.recommended, ...tseslint.configs.recommendedTypeChecked],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.test.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      globals: globals.node,
    },
    rules: {
      // Nothing in this package logs automatically; diagnostics are never
      // uploaded and never emitted as a side effect
      // (docs/architecture/diagnostics.md).
      'no-console': 'error',
      eqeqeq: ['error', 'always'],
      '@typescript-eslint/explicit-function-return-type': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
    },
  },

  {
    files: ['**/*.js', '**/*.cjs', '**/*.mjs'],
    extends: [js.configs.recommended],
    languageOptions: {
      globals: { ...globals.node },
    },
  },
);
