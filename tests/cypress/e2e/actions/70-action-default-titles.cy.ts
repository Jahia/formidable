import gql from 'graphql-tag';
import {createFormNode} from '../../support/fixtures/forms';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from '../support/useFormidableSite';

const FORM_NAME = 'action-default-titles-form';
const ACTIONS_PATH = `${CONTENT_PATH}/${FORM_NAME}/actions`;

const GET_CREATE_FORM_TITLE_DEFAULT = gql`
	query getCreateFormTitleDefault($nodeType: String!, $uiLocale: String!, $locale: String!, $parentPath: String!) {
		forms {
			createForm(primaryNodeType: $nodeType, uiLocale: $uiLocale, locale: $locale, uuidOrPath: $parentPath) {
				sections {
					fieldSets {
						fields {
							name
							defaultValues {
								string
							}
						}
					}
				}
			}
		}
	}
`;

type CreateFormResponse = {
	errors?: Array<{message: string}>;
	data?: {
		forms?: {
			createForm?: {
				sections: Array<{
					fieldSets: Array<{
						fields: Array<{
							name: string;
							defaultValues?: Array<{string: string}> | null;
						}>;
					}>;
				}>;
			};
		};
	};
};

const EXPECTED_DEFAULT_TITLES: Array<{nodeType: string; en: string; fr: string}> = [
	{nodeType: 'fmdb:save2jcrAction', en: 'Save to JCR', fr: 'Sauvegarde JCR'},
	{nodeType: 'fmdb:forwardAction', en: 'Forward Data', fr: 'Transfert de données'},
	{nodeType: 'fmdb:emailNotificationAction', en: 'Email Notification', fr: 'Notification email'},
	{nodeType: 'fmdb:emailContentAction', en: 'Email Form Content', fr: 'Contenu du formulaire par email'}
];

const assertTitleDefault = (nodeType: string, locale: string, expectedTitle: string) => {
	cy.apollo({
		query: GET_CREATE_FORM_TITLE_DEFAULT,
		variables: {nodeType, uiLocale: locale, locale, parentPath: ACTIONS_PATH}
	}).then((response: CreateFormResponse) => {
		expect(response.errors, `GraphQL errors for ${nodeType} (${locale})`).to.be.undefined;

		const fields = (response.data?.forms?.createForm?.sections ?? [])
			.flatMap(section => section.fieldSets)
			.flatMap(fieldSet => fieldSet.fields);
		const titleField = fields.find(field => field.name === 'jcr:title');

		expect(titleField, `jcr:title field in ${nodeType} create form (${locale})`).to.not.be.undefined;
		expect(titleField.defaultValues?.map(value => value.string), `jcr:title default for ${nodeType} (${locale})`)
			.to.deep.equal([expectedTitle]);
	});
};

describe('Form actions - 70 Default titles', () => {
	useFormidableSite();

	before(() => {
		cy.login();
		// The actions list only exists once the form holds at least one action
		createFormNode(FORM_NAME, 'Action Default Titles Form', [], {
			actions: [{
				name: 'storeSubmission',
				primaryNodeType: 'fmdb:save2jcrAction',
				properties: []
			}]
		});
		cy.logout();
	});

	it('prefills jcr:title with the action type display name in the create form', () => {
		EXPECTED_DEFAULT_TITLES.forEach(({nodeType, en, fr}) => {
			assertTitleDefault(nodeType, 'en', en);
			assertTitleDefault(nodeType, 'fr', fr);
		});
	});
});
