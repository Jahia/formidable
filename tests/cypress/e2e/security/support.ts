import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_IDS} from '../../support/constants';
import {FORMIDABLE_TEST_SITE, LiveFormPageInfo} from '../../support/fixtures';

const DIRECT_SUBMIT_PATH = '/modules/formidable-engine/form-submit';
const DEFAULT_BOUNDARY = '----FormidableCypressBoundary';

interface DirectSubmissionOptions {
	body?: string;
	fields?: Record<string, string>;
	formId?: string;
	headers?: Record<string, string>;
	lang?: string;
}

interface DirectXhrResult {
	body: any;
	rawBody: string;
	status: number;
}

export const SAME_ORIGIN = 'http://localhost:8080';
export const FOREIGN_ORIGIN = 'https://evil.example';

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

export const withSameOriginHeaders = (headers: Record<string, string> = {}): Record<string, string> => ({
	Origin: SAME_ORIGIN,
	Referer: `${SAME_ORIGIN}/`,
	...headers
});

export const expectErrorResponse = (
		response: Cypress.Response<any>,
		status: number,
		errorCode: string
) => {
	expect(response.status).to.eq(status);
	expect(response.body).to.deep.equal({
		success: false,
		errorCode
	});
};

export const expectSuccessResponse = (response: Cypress.Response<any>) => {
	expect(response.status).to.eq(200);
	expect(response.body).to.deep.equal({success: true});
};

export const expectCsrfRejectedResponse = (response: Cypress.Response<any>) => {
	expect(response.status).to.eq(403);
};

export const postDirectMultipartSubmission = ({
	body,
	fields = {},
	formId,
	headers = {},
	lang = 'en'
}: DirectSubmissionOptions): Cypress.Chainable<Cypress.Response<any>> => {
	const query = new URLSearchParams({lang});
	if (formId) {
		query.set('fid', formId);
	}

	const multipart = body ?? buildMultipartBody(fields);

	return cy.request({
		method: 'POST',
		url: `${DIRECT_SUBMIT_PATH}?${query.toString()}`,
		failOnStatusCode: false,
		headers: {
			'Content-Type': `multipart/form-data; boundary=${DEFAULT_BOUNDARY}`,
			...headers
		},
		body: multipart
	});
};

export const postDirectNonMultipartSubmission = (
	formId: string,
	headers: Record<string, string> = {}
): Cypress.Chainable<Cypress.Response<any>> => {
	const query = new URLSearchParams({fid: formId, lang: 'en'});

	return cy.request({
		method: 'POST',
		url: `${DIRECT_SUBMIT_PATH}?${query.toString()}`,
		failOnStatusCode: false,
		headers: {
			'Content-Type': 'application/json',
			...headers
		},
		body: JSON.stringify({fullName: 'Mallory'})
	});
};

export const liveFormPath = (form: Pick<LiveFormPageInfo, 'livePath'>) =>
	`/en/sites/${FORMIDABLE_TEST_SITE.key}/${form.livePath}`;

export const postDirectMultipartSubmissionViaPatchedXhr = ({
	formId,
	fields = {},
	lang = 'en'
}: Required<Pick<DirectSubmissionOptions, 'formId'>> & Pick<DirectSubmissionOptions, 'fields' | 'lang'>)
		: Cypress.Chainable<DirectXhrResult> => {
	const query = new URLSearchParams({fid: formId, lang});
	const url = `${DIRECT_SUBMIT_PATH}?${query.toString()}`;

	return cy.window().then(win => (
		new Cypress.Promise<DirectXhrResult>((resolve, reject) => {
			const formData = new win.FormData();
			Object.entries(fields).forEach(([name, value]) => {
				formData.append(name, value);
			});

			const xhr = new win.XMLHttpRequest();
			xhr.open('POST', url, true);
			xhr.withCredentials = true;
			xhr.onload = () => {
				resolve({
					status: xhr.status,
					rawBody: xhr.responseText,
					body: parseJsonSafely(xhr.responseText)
				});
			};
			xhr.onerror = () => reject(new Error(`Direct XHR submission failed for ${url}`));
			xhr.send(formData);
		})
	));
};

function buildMultipartBody(fields: Record<string, string>): string {
	return [
		...Object.entries(fields).map(([name, value]) => (
			`--${DEFAULT_BOUNDARY}\r\n`
			+ `Content-Disposition: form-data; name="${name}"\r\n\r\n`
			+ `${value}\r\n`
		)),
		`--${DEFAULT_BOUNDARY}--\r\n`
	].join('');
}

function parseJsonSafely(rawBody: string): any {
	try {
		return JSON.parse(rawBody);
	} catch {
		return rawBody;
	}
}
