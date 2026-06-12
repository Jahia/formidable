import {getNodeByPath} from '@jahia/cypress';
import {createPublishedLiveFormPage, getInputTextNode} from '../../support/fixtures';
import {FORMIDABLE_TEST_SITE} from '../../support/fixtures';
import {
	expectSuccessResponse,
	postDirectMultipartSubmission,
	useFormidableSite,
	withSameOriginHeaders
} from './support';

interface JcrPropertyResponse {
	name: string;
	value?: string | null;
	values?: string[] | null;
}

interface JcrChildNodeResponse {
	name: string;
	children?: {
		nodes?: Array<{
			properties?: JcrPropertyResponse[];
		}>;
	};
}

interface NodeByPathResponse {
	data?: {
		jcr?: {
			nodeByPath?: {
				children?: {
					nodes?: JcrChildNodeResponse[];
				};
				properties?: JcrPropertyResponse[];
			};
		};
	};
}

const SAVE_TO_JCR_ACTION = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: [] as Array<{name: string; value: string}>
};

const buildSubmissionDayPath = (formName: string, submissionDate: Date): string => {
	const [year, month, day] = submissionDate.toISOString().slice(0, 10).split('-');
	return `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${formName}/submissions/${year}/${month}/${day}`;
};

describe('Security - submission tampering', () => {
	useFormidableSite();

	let publicFormId: string;

	before(() => {
		cy.login();

		createPublishedLiveFormPage(
			'tampering-form',
			'Tampering form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'tampering-form-page',
			'Tampering form page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(({formId}) => {
			publicFormId = formId;
		});

		cy.logout();
	});

	it('ignores an undeclared text field in the submitted multipart body', () => {
		cy.logout();

		const submittedAt = new Date();

		postDirectMultipartSubmission({
			formId: publicFormId,
			fields: {fullName: 'Alice', role: 'admin'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectSuccessResponse(response);

			const dayPath = buildSubmissionDayPath('tampering-form', submittedAt);

			getNodeByPath(dayPath, [], '', ['fmdb:formSubmission'], 'LIVE')
				.then((nodeResponse: NodeByPathResponse) => {
					const submissions = nodeResponse.data?.jcr?.nodeByPath?.children?.nodes ?? [];
					expect(submissions).to.have.length.greaterThan(0);

					const submissionName = submissions[submissions.length - 1].name;

					// Read the data child node to inspect stored field values.
					getNodeByPath(
						`${dayPath}/${submissionName}/data`,
						['fullName', 'role'],
						'',
						[],
						'LIVE'
					).then((dataResponse: NodeByPathResponse) => {
						const properties = dataResponse.data?.jcr?.nodeByPath?.properties ?? [];

						const fullNameProp = properties.find(p => p.name === 'fullName');
						const roleProp = properties.find(p => p.name === 'role');

						// The declared field must be stored.
						expect(fullNameProp, 'declared field "fullName" should be stored').to.exist;
						expect(fullNameProp?.value).to.eq('Alice');

						// The undeclared field must not be stored.
						expect(roleProp, 'undeclared field "role" should not be stored').to.not.exist;
					});
				});
		});
	});

	it('ignores an undeclared file part in the submitted multipart body', () => {
		cy.logout();

		const submittedAt = new Date();
		const boundary = '----FormidableCypressBoundary';

		// Build a multipart body with the declared text field and an undeclared file part.
		const body =
			`--${boundary}\r\n` +
			`Content-Disposition: form-data; name="fullName"\r\n\r\n` +
			`Bob\r\n` +
			`--${boundary}\r\n` +
			`Content-Disposition: form-data; name="maliciousFile"; filename="evil.txt"\r\n` +
			`Content-Type: text/plain\r\n\r\n` +
			`malicious content\r\n` +
			`--${boundary}--\r\n`;

		postDirectMultipartSubmission({
			formId: publicFormId,
			body,
			headers: withSameOriginHeaders()
		}).then(response => {
			expectSuccessResponse(response);

			const dayPath = buildSubmissionDayPath('tampering-form', submittedAt);

			getNodeByPath(dayPath, [], '', ['fmdb:formSubmission'], 'LIVE')
				.then((nodeResponse: NodeByPathResponse) => {
					const submissions = nodeResponse.data?.jcr?.nodeByPath?.children?.nodes ?? [];
					expect(submissions).to.have.length.greaterThan(0);

					const submissionName = submissions[submissions.length - 1].name;

					// Verify the declared text field is stored.
					getNodeByPath(
						`${dayPath}/${submissionName}/data`,
						['fullName', 'maliciousFile'],
						'',
						[],
						'LIVE'
					).then((dataResponse: NodeByPathResponse) => {
						const properties = dataResponse.data?.jcr?.nodeByPath?.properties ?? [];

						const fullNameProp = properties.find(p => p.name === 'fullName');
						const maliciousFileProp = properties.find(p => p.name === 'maliciousFile');

						expect(fullNameProp, 'declared field "fullName" should be stored').to.exist;
						expect(fullNameProp?.value).to.eq('Bob');
						expect(maliciousFileProp, 'undeclared field "maliciousFile" should not be stored in data').to.not.exist;
					});

					// Verify no file metadata or binary is stored for the undeclared file field.
					getNodeByPath(
						`${dayPath}/${submissionName}`,
						[],
						'',
						['jnt:folder'],
						'LIVE'
					).then((submissionResponse: NodeByPathResponse) => {
						const children = submissionResponse.data?.jcr?.nodeByPath?.children?.nodes ?? [];
						const filesFolder = children.find(c => c.name === 'files');

						if (filesFolder) {
							// If a files folder exists (from a previous test), the undeclared field
							// must not have a subfolder.
							const fileFieldFolders = filesFolder.children?.nodes ?? [];
							const maliciousFolder = fileFieldFolders.find(f => f.name === 'maliciousFile');
							expect(maliciousFolder, 'undeclared file field should not have a storage folder').to.not.exist;
						}
					});
				});
		});
	});
});

