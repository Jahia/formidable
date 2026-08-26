import gql from 'graphql-tag';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from './support';

const FORM_NAME = 'legacy-options-form';
const SELECT_PATH = `${CONTENT_PATH}/${FORM_NAME}/fields/legacySelect`;
const RADIO_PATH = `${CONTENT_PATH}/${FORM_NAME}/fields/legacyRadio`;

const GET_MIGRATED_FIELD = gql`
	query getMigratedField($path: String!, $workspace: Workspace!) {
		jcr(workspace: $workspace) {
			nodeByPath(path: $path) {
				mixinTypes {
					name
				}
				optionsMode: property(name: "fmdb:optionsMode") {
					value
				}
				options: property(name: "fmdb:options", language: "en") {
					values
				}
			}
		}
	}
`;

type MigratedFieldResponse = {
	errors?: Array<{message: string}>;
	data?: {
		jcr?: {
			nodeByPath?: {
				mixinTypes?: Array<{name: string}>;
				optionsMode?: {value?: string} | null;
				options?: {values?: string[]} | null;
			};
		};
	};
};

const getMigratedField = (path: string, workspace: 'EDIT' | 'LIVE') =>
	cy.apollo({query: GET_MIGRATED_FIELD, variables: {path, workspace}});

const expectMigrated = (
	path: string,
	workspace: 'EDIT' | 'LIVE',
	expectedOptions: Array<{value: string; label: string; selected: boolean}>
) => {
	getMigratedField(path, workspace).then((response: MigratedFieldResponse) => {
		const node = response.data?.jcr?.nodeByPath;
		const scope = `${path} (${workspace})`;

		expect(node?.mixinTypes?.map(mixin => mixin.name), scope).to.include('fmdbmix:manualOptions');
		expect(node?.optionsMode?.value, scope).to.eq('manual');

		const options = (node?.options?.values ?? []).map(raw => JSON.parse(raw));
		expect(options, scope).to.deep.equal(expectedOptions);
	});
};

describe('Form fields - 219 Choice options migration', () => {
	useFormidableSite();

	it('migrates 0.3-style choice fields to the unified options when the engine starts', () => {
		// Bare choice fields, then the legacy per-type option properties are written on
		// their translation subnodes — the exact storage a 0.3 site leaves behind.
		createPublishedLiveFormPage(
			FORM_NAME,
			'Legacy Options Form',
			[
				{
					name: 'legacySelect',
					primaryNodeType: 'fmdb:select',
					mixins: [],
					properties: [{name: 'jcr:title', value: 'Legacy select', language: 'en'}]
				},
				{
					name: 'legacyRadio',
					primaryNodeType: 'fmdb:radio',
					mixins: [],
					properties: [{name: 'jcr:title', value: 'Legacy radio', language: 'en'}]
				}
			]
		).then(({livePath}) => {
			cy.executeGroovy('groovy/simulateLegacyChoiceOptions.groovy', {
				__FIELD_PATH__: SELECT_PATH,
				__LEGACY_PROPERTY__: 'options',
				__PAIRS__: 'red:Red,green:Green',
				__SELECTED__: 'green'
			}).then(result => cy.log(String(result)));

			cy.executeGroovy('groovy/simulateLegacyChoiceOptions.groovy', {
				__FIELD_PATH__: RADIO_PATH,
				__LEGACY_PROPERTY__: 'choices',
				__PAIRS__: 'yes:Yes,no:No',
				__SELECTED__: ''
			}).then(result => cy.log(String(result)));

			// The migration is keyed on content state (a legacy property is present) and
			// runs at module activation: restarting the engine is the upgrade trigger.
			cy.executeGroovy('groovy/restartFormidableEngine.groovy', {})
				.then(result => cy.log(String(result)));

			// Module activation is asynchronous: wait until the migration stamped the mode.
			cy.waitUntil(
				() => getMigratedField(SELECT_PATH, 'EDIT').then(
					(response: MigratedFieldResponse) =>
						response.data?.jcr?.nodeByPath?.optionsMode?.value === 'manual'
				),
				{timeout: 60000, interval: 2000, errorMsg: 'the migration never stamped fmdb:optionsMode'}
			);

			// Both legacy property names, in both workspaces, values moved as-is.
			expectMigrated(SELECT_PATH, 'EDIT', [
				{value: 'red', label: 'Red', selected: false},
				{value: 'green', label: 'Green', selected: true}
			]);
			expectMigrated(SELECT_PATH, 'LIVE', [
				{value: 'red', label: 'Red', selected: false},
				{value: 'green', label: 'Green', selected: true}
			]);
			expectMigrated(RADIO_PATH, 'EDIT', [
				{value: 'yes', label: 'Yes', selected: false},
				{value: 'no', label: 'No', selected: false}
			]);
			expectMigrated(RADIO_PATH, 'LIVE', [
				{value: 'yes', label: 'Yes', selected: false},
				{value: 'no', label: 'No', selected: false}
			]);

			// The legacy property is moved, not copied: nothing remains on the
			// translation node.
			cy.apollo({
				query: gql`
					query getLegacyResidue($path: String!) {
						jcr {
							nodeByPath(path: $path) {
								legacy: property(name: "options") {
									values
								}
							}
						}
					}
				`,
				variables: {path: `${SELECT_PATH}/j:translation_en`}
			}).then((response: {data?: {jcr?: {nodeByPath?: {legacy?: unknown}}}}) => {
				expect(response.data?.jcr?.nodeByPath?.legacy, 'legacy property left on the translation node')
					.to.be.null;
			});

			// The published form renders the migrated options without a republish.
			const form = visitLiveForm(livePath);
			form.getSelectInput('legacySelect')
				.shouldBeVisible()
				.shouldHaveOption('Red')
				.shouldHaveSelectedOption('Green');
			form.getRadioGroup('legacyRadio').getRadio('Yes').shouldHaveValue('yes');
			form.getRadioGroup('legacyRadio').getRadio('No').shouldHaveValue('no');
		});
	});
});
