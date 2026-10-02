const tsJest = require('ts-jest');
const createTransformer = tsJest.default
  ? tsJest.default.createTransformer
  : tsJest.createTransformer;

function createCustomTransformer(options) {
  const base = createTransformer(options);
  return {
    ...base,
    process(sourceText, sourcePath, transformOptions) {
      const res = base.process(sourceText, sourcePath, transformOptions);
      if (res && typeof res.code === 'string') {
        const prefix = 'sourceMappingURL=data:application/json;charset=utf-8;base64,';
        const idx = res.code.lastIndexOf(prefix);
        if (idx !== -1) {
          try {
            const b64 = res.code.slice(idx + prefix.length);
            const map = JSON.parse(Buffer.from(b64, 'base64').toString('utf8'));
            if (Array.isArray(map.sources)) {
              map.sources = map.sources.map((s) => s.replace(/^file:\/\//, ''));
            }
            const updatedB64 = Buffer.from(JSON.stringify(map), 'utf8').toString('base64');
            return {
              ...res,
              code: res.code.slice(0, idx + prefix.length) + updatedB64,
            };
          } catch {
            // A malformed inline source map is not a transform failure: fall
            // through and return ts-jest's original output unchanged.
          }
        }
      }
      return res;
    },
    getCacheKey(sourceText, sourcePath, transformOptions) {
      return (
        'clean-sources-v1:' +
        (base.getCacheKey ? base.getCacheKey(sourceText, sourcePath, transformOptions) : '')
      );
    },
  };
}

module.exports = {
  createTransformer: createCustomTransformer,
};
