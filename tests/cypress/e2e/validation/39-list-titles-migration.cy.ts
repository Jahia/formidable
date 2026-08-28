import gql from 'graphql-tag';
import {createPublishedLiveFormPage} from '../../support/fixtures';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from './support';

const FORM_NAME = 'list-titles-form';
const FORM_PATH = `${CONTENT_PATH}/${FORM_NAME}`;

const GET_LIST_TITLES = gql`
	query getListTitles($path: String!, $workspace: Workspace!) {
		jcr(workspace: $workspace) {
			nodeByPath(path: $path) {
				fields: descendant(relPath: "fields") {
					en: displayName(language: "en")
					fr: displayName(language: "fr")
				}
				actions: descendant(relPath: "actions") {
					en: displayName(language: "en")
					fr: displayName(language: "fr")
				}
			}
		}
	}
`;

const SET_ACTIONS_TITLE = gql`
	mutation setActionsTitle($path: String!, $title: String!) {
		jcr {
			mutateNode(pathOrId: $path) {
				mutateProperty(name: "jcr:title") {
					setValue(language: "en", value: $title)
				}
			}
		}
	}
`;

type ListTitlesResponse = {
	data?: {
		jcr?: {
			nodeByPath?: {
				fields?: {en?: string; fr?: string};
				actions?: {en?: string; fr?: string};
			};
		};
	};
};

const getListTitles = (workspace: 'EDIT' | 'LIVE') =>
	cy.apollo({query: GET_LIST_TITLES, variables: {path: FORM_PATH, workspace}});

const expectDefaultTitles = (workspace: 'EDIT' | 'LIVE', actionsEn = 'Form actions') => {
	getListTitles(workspace).then((response: ListTitlesResponse) => {
		const form = response.data?.jcr?.nodeByPath;
		expect(form?.fields?.en, `${workspace} fields (en)`).to.equal('Form fields');
		expect(form?.fields?.fr, `${workspace} fields (fr)`).to.equal('Champs du formulaire');
		expect(form?.actions?.en, `${workspace} actions (en)`).to.equal(actionsEn);
		expect(form?.actions?.fr, `${workspace} actions (fr)`).to.equal('Actions du formulaire');
	});
};

/**
 * The 'fields' and 'actions' lists of a form carry a translatable title (the Page Builder
 * shows it on their box and in the create-button tooltips instead of the bare node name).
 * New forms get it at creation; forms stored before it existed get it from the startup
 * migration, in every site language and both workspaces — a title a contributor set
 * is never overridden, and a second run changes nothing.
 */
describe('Validation - 39 Field and action list titles', () => {
	useFormidableSite();

	it('gives new lists a translated default title, and restores missing ones at startup without touching custom ones', () => {
		createPublishedLiveFormPage(FORM_NAME, 'List Titles Form', []).then(() => {
			// Fresh form: the default title in the site languages, published along with it.
			expectDefaultTitles('EDIT');
			expectDefaultTitles('LIVE');

			// A form stored before the lists had a title, with a contributor's own title on
			// the actions (english) that the migration must keep.
			cy.executeGroovy('groovy/removeListTitles.groovy', {__FORM_PATH__: FORM_PATH});
			cy.apollo({mutation: SET_ACTIONS_TITLE, variables: {path: `${FORM_PATH}/actions`, title: 'My actions'}})
				.then(response => expect(response.errors, 'set custom title').to.be.undefined);

			// Restarting the engine is the upgrade trigger.
			cy.executeGroovy('groovy/restartFormidableEngine.groovy', {});
			expectDefaultTitles('EDIT', 'My actions');
			expectDefaultTitles('LIVE');

			// Idempotence: a second run leaves everything as it stands.
			cy.executeGroovy('groovy/restartFormidableEngine.groovy', {});
			expectDefaultTitles('EDIT', 'My actions');
			expectDefaultTitles('LIVE');
		});
	});
});
