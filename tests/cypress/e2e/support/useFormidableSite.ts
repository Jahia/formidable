import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_IDS} from '../../support/constants';
import {FORMIDABLE_TEST_SITE} from '../../support/fixtures';

export const useFormidableSite = (config: Partial<typeof FORMIDABLE_TEST_SITE.config> = {}) => {
	before(() => {
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, {...FORMIDABLE_TEST_SITE.config, ...config});
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
	});

	beforeEach(() => {
		cy.login();
	});

	afterEach(() => {
		cy.logout();
	});
};
