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

/**
 * The option VALUES of a manual choice field are its identity — submissions
 * store them, conditional logic matches them, the forged-value validation
 * checks them — so every language must share one set. The site's default
 * language is the authority: saving fmdb:options in any language re-aligns the
 * other languages on the master's values, order and count, keeping each
 * language's own labels for the values it already carries.
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
});
