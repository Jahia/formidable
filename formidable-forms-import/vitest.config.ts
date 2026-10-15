import {defineConfig} from 'vitest/config';

// The tests run without the federation plugin of vite.config.ts: it serves the build, and its types extraction
// keeps the test server from exiting once the tests have passed.
export default defineConfig({});
