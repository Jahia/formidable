import {defineConfig} from 'vitest/config';

// Standalone config: vitest must NOT pick up vite.config.ts, whose
// module-federation plugin spawns watchers that never exit under vitest.
export default defineConfig({
    test: {
        include: ['src/**/*.test.ts'],
        environment: 'node'
    }
});
