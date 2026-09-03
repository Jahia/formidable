import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_IDS} from '../../support/constants';
import {FORMIDABLE_TEST_SITE} from '../../support/fixtures';

/** The browser-local calendar day, the same way a hydrated date input resolves it. */
export const localDay = (offsetDays = 0): string => {
	const day = new Date();
	day.setDate(day.getDate() + offsetDays);
	const month = String(day.getMonth() + 1).padStart(2, '0');
	const date = String(day.getDate()).padStart(2, '0');
	return `${day.getFullYear()}-${month}-${date}`;
};

export const useFormidableSite = () => {
	before(() => {
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, FORMIDABLE_TEST_SITE.config);
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
	});

	beforeEach(() => {
		cy.login();
	});

	afterEach(() => {
		cy.logout();
	});
};
