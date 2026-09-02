import gql from 'graphql-tag';
import {addNode, createSite, deleteSite, enableModule} from '@jahia/cypress';
import {createPublishedLiveFormPage, getInputTextNode} from '../../support/fixtures';
import {FORMIDABLE_TEST_SITE} from '../../support/fixtures/site';
import {useFormidableSite} from './support';

const SITE_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}`;
// A second, throwaway site: its healing in the same pass is the proof that the
// pass ran, which the negative assertion on the main site needs.
const WITNESS_KEY = 'ReactivationWitness';
const WITNESS_PATH = `/sites/${WITNESS_KEY}`;

const getSiteState = (path: string = SITE_PATH) => cy.apollo({
	query: gql`
		query siteModules($path: String!) {
			jcr {
				nodeByPath(path: $path) {
					modules: property(name: "j:installedModules") { values }
					mixins: mixinTypes { name }
				}
			}
		}
	`,
	variables: {path}
});

type SiteStateResponse = {
	data?: {jcr?: {nodeByPath?: {
		modules?: {values?: string[]};
		mixins?: Array<{name: string}>;
	}}};
};

const siteHasElements = (path: string = SITE_PATH) => getSiteState(path).then((response: SiteStateResponse) =>
	(response.data?.jcr?.nodeByPath?.modules?.values ?? []).includes('formidable-elements'));

const restartElements = () => cy.executeGroovy('groovy/restartModuleBundle.groovy', {
	__MODULE_ID__: 'formidable-elements'
}).then(result => cy.log(String(result)));

const orphanSite = (path: string = SITE_PATH) => cy.executeGroovy('groovy/removeSiteModule.groovy', {
	__SITE_PATH__: path,
	__MODULE_ID__: 'formidable-elements'
}).then(result => cy.log(String(result)));

/**
 * The 0.3 -> 0.4 identity swap drops formidable-elements from every site's installed
 * list; the engine heals a form-bearing site when the elements module deploys — ONCE:
 * the healed site is stamped, and a later deliberate deactivation (a decommissioned
 * site keeping archived forms) sticks. Exercised through a real bundle restart, the
 * event the healing listens to: a guard regression that leaves it silently inert on
 * this path turns this spec red (it did once, pre-merge).
 */
describe('Validation - 46 Elements re-enabled on form-bearing sites', () => {
	useFormidableSite();

	it('heals an orphaned site once, then respects a deliberate deactivation', () => {
		createPublishedLiveFormPage(
			'reactivation-form',
			'Reactivation Form',
			[getInputTextNode({name: 'anything', title: 'Anything'})]
		).then(() => {
			// The upgrade's orphaned state: forms present, module gone from the site.
			orphanSite();
			restartElements();

			// The start-fired redeploy event heals the site and stamps the marker.
			cy.waitUntil(
				() => siteHasElements(),
				{timeout: 60000, interval: 2000, errorMsg: 'the site was never healed'}
			);
			getSiteState().then((response: SiteStateResponse) => {
				expect(response.data?.jcr?.nodeByPath?.mixins?.map(m => m.name))
					.to.include('fmdbmix:elementsReactivated');
			});

			// Deliberate deactivation of a marked site: the healing must NOT undo it.
			// A non-event needs a proof the pass ran: a witness site — orphaned,
			// unmarked, form-bearing — is healed by the SAME pass; once it is, the
			// main site's continued absence is a verdict, not a race.
			deleteSite(WITNESS_KEY);
			createSite(WITNESS_KEY, FORMIDABLE_TEST_SITE.config);
			enableModule('formidable-elements', WITNESS_KEY);
			addNode({
				parentPathOrId: `${WITNESS_PATH}/contents`,
				name: 'witness-form',
				primaryNodeType: 'fmdb:form',
				properties: [{name: 'jcr:title', value: 'Witness', language: 'en'}]
			});
			orphanSite(WITNESS_PATH);
			orphanSite();

			restartElements();
			cy.waitUntil(
				() => siteHasElements(WITNESS_PATH),
				{timeout: 60000, interval: 2000, errorMsg: 'the witness was never healed — the pass did not run'}
			);
			siteHasElements().then(has => expect(has, 'deliberate deactivation sticks').to.eq(false));
			getSiteState().then((response: SiteStateResponse) => {
				expect(response.data?.jcr?.nodeByPath?.mixins?.map(m => m.name),
					'the one-shot marker is what protected the site').to.include('fmdbmix:elementsReactivated');
			});
			deleteSite(WITNESS_KEY);

			// Leave the shared site usable for whoever runs next: the manual gesture,
			// which the one-shot marker never blocks.
			enableModule('formidable-elements', FORMIDABLE_TEST_SITE.key);
			cy.waitUntil(() => siteHasElements(), {timeout: 30000, interval: 1000});
		});
	});
});
