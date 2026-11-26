import {defineConfig} from 'vitest/config';

// The tests run without the federation plugin of vite.config.ts: it serves the build, and its types extraction
// keeps the test server from exiting once the tests have passed. Moonstone is inlined: Node cannot load the CSS file
// its entry imports, and Vite can.
export default defineConfig({
    test: {
        server: {
            deps: {
                inline: ['@jahia/moonstone']
            }
        }
    }
});
