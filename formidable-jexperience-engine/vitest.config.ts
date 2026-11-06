import {defineConfig} from 'vitest/config';

// The tests run without the federation plugin of vite.config.ts: it serves the build, and its types extraction
// keeps the test server from exiting once the tests have passed.
// One registry, as module federation shares one in jContent: the engine's code the tests import resolves
// @jahia/ui-extender from the engine's own node_modules otherwise.
export default defineConfig({
    resolve: {
        dedupe: ['@jahia/ui-extender']
    },
    test: {
        include: ['src/javascript/**/*.test.ts']
    }
});
