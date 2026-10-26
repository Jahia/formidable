import {defineConfig} from 'cypress'
import base from './cypress.config'

// The PR workflow (on-code-change.yml) splits the suite over three runners, one config file each,
// passed as tests_profile. The first two shards name their folders of cypress/e2e/; the last one
// runs everything else, so a new folder always runs somewhere.
const SHARDS = {
	fields: ['fields', 'migrations'],
	validation: ['validation', 'security'],
}

const specsOf = (folders: string[]) => folders.map(folder => `cypress/e2e/${folder}/**/*.cy.ts`)

export const shardConfig = (shard: keyof typeof SHARDS | 'rest') => defineConfig({
	...base,
	e2e: shard === 'rest'
		? {...base.e2e, excludeSpecPattern: [...base.e2e.excludeSpecPattern as string[], ...specsOf(Object.values(SHARDS).flat())]}
		: {...base.e2e, specPattern: specsOf(SHARDS[shard])},
})
