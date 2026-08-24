import gql from 'graphql-tag';
import {createFormNode, getSelectNode} from '../../support/fixtures';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from './support';

const option = (value: string, label: string, selected = false): string =>
	JSON.stringify({value, label, selected});

const SET_FR_OPTIONS = gql`
	mutation setFrOptions($path: String!, $values: [String!]!) {
		jcr {
			mutateNode(pathOrId: $path) {
				mutateProperty(name: "fmdb:options") {
					setValues(language: "fr", values: $values)
				}
			}
		}
	}
`;

const GET_FR_OPTIONS = gql`
	query getFrOptions($path: String!) {
		jcr {
			nodeByPath(path: $path) {
				uuid
				workspace
				property(name: "fmdb:options", language: "fr") {
					values
				}
			}
		}
	}
`;

const GET_EN_OPTIONS = gql`
	query getEnOptions($path: String!) {
		jcr {
			nodeByPath(path: $path) {
				uuid
				workspace
				property(name: "fmdb:options", language: "en") {
					values
				}
			}
		}
	}
`;

/**
 * The option VALUES of a manual choice field are its identity — submissions
 * store them, conditional logic matches them, the forged-value validation
 * checks them — so every language must share one set. The site's default
 * language is the authority: saving fmdb:options in any language re-aligns the
 * EXISTING translations on the master's values, order and count, keeping each
 * language's own labels for the values it already carries. A language nobody
 * translated stays untranslated (starting a translation is the contributor's
 * gesture, e.g. Copy a language), and rows carrying no value at all — the only
 * thing an editor can save from another language — are noise the sync cleans.
 */
describe('Form fields - 221 Choice options language sync', () => {
	useFormidableSite();

	it('re-aligns a diverging translation on the default language structure', () => {
		const formName = `options-lang-sync-${Date.now()}`;
		const fieldPath = `${CONTENT_PATH}/${formName}/fields/flavor`;

		createFormNode(formName, formName, [
			getSelectNode({
				name: 'flavor',
				title: 'Flavor',
				options: [
					{value: 'vanilla', label: 'Vanilla'},
					{value: 'chocolate', label: 'Chocolate'}
				]
			})
		]).then(() => {
			// A translation saved with its own values, order and count: 'mint' does
			// not exist in the master, 'chocolate' does (with a French label).
			cy.apollo({
				mutation: SET_FR_OPTIONS,
				variables: {
					path: fieldPath,
					values: [option('mint', 'Menthe'), option('chocolate', 'Chocolat')]
				}
			}).then(result => {
				expect(result.errors, 'divergent save accepted').to.equal(undefined);
			});

			// The observation listener re-aligns asynchronously: the French list
			// converges to the master's values in the master's order — vanilla
			// inherited from the master, chocolate keeping its French label, mint
			// dropped as a value the identity set does not contain.
			cy.waitUntil(() =>
				cy.apollo({query: GET_FR_OPTIONS, variables: {path: fieldPath}, fetchPolicy: 'no-cache'})
					.then(result => {
						const values = result?.data?.jcr?.nodeByPath?.property?.values as string[] | undefined;
						return Boolean(values)
							&& values!.length === 2
							&& values![0] === option('vanilla', 'Vanilla')
							&& values![1] === option('chocolate', 'Chocolat');
					}), {timeout: 15000, interval: 500, errorMsg: 'French options never re-aligned on the master'});
		});
	});

	it('cleans valueless rows instead of marking the language translated', () => {
		const formName = `options-junk-clean-${Date.now()}`;
		const fieldPath = `${CONTENT_PATH}/${formName}/fields/flavor`;

		createFormNode(formName, formName, [
			getSelectNode({
				name: 'flavor',
				title: 'Flavor',
				options: [
					{value: 'vanilla', label: 'Vanilla'},
					{value: 'chocolate', label: 'Chocolate'}
				]
			})
		]).then(() => {
			// What an accidental "add" clicked in another language saves: a row that
			// never received a value (no value is typable outside the default
			// language). This must NOT turn French into a translated language.
			cy.apollo({
				mutation: SET_FR_OPTIONS,
				variables: {path: fieldPath, values: ['']}
			}).then(result => {
				expect(result.errors, 'valueless save accepted').to.equal(undefined);
			});

			// The sync removes the noise: French goes back to untranslated instead
			// of adopting the master's entries.
			cy.waitUntil(() =>
				cy.apollo({query: GET_FR_OPTIONS, variables: {path: fieldPath}, fetchPolicy: 'no-cache'})
					.then(result => result?.data?.jcr?.nodeByPath?.property === null),
				{timeout: 15000, interval: 500, errorMsg: 'valueless French options were never cleaned'});

			// The master is untouched by the cleanup.
			cy.apollo({query: GET_EN_OPTIONS, variables: {path: fieldPath}, fetchPolicy: 'no-cache'})
				.then(result => {
					const values = result?.data?.jcr?.nodeByPath?.property?.values as string[];
					expect(values).to.deep.equal([
						option('vanilla', 'Vanilla'),
						option('chocolate', 'Chocolate')
					]);
				});
		});
	});
});
