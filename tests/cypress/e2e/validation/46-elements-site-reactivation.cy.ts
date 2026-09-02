import gql from 'graphql-tag';
import {enableModule} from '@jahia/cypress';
import {createPublishedLiveFormPage, getInputTextNode} from '../../support/fixtures';
import {FORMIDABLE_TEST_SITE} from '../../support/fixtures/site';
import {useFormidableSite} from './support';

const SITE_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}`;

const getSiteState = () => cy.apollo({
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
	variables: {path: SITE_PATH}
});

type SiteStateResponse = {
	data?: {jcr?: {nodeByPath?: {
		modules?: {values?: string[]};
		mixins?: Array<{name: string}>;
	}}};
};

const siteHasElements = () => getSiteState().then((response: SiteStateResponse) =>
	(response.data?.jcr?.nodeByPath?.modules?.values ?? []).includes('formidable-elements'));

const restartElements = () => cy.executeGroovy('groovy/restartModuleBundle.groovy', {
	__MODULE_ID__: 'formidable-elements'
}).then(result => cy.log(String(result)));

const orphanSite = () => cy.executeGroovy('groovy/removeSiteModule.groovy', {
	__SITE_PATH__: SITE_PATH,
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
			orphanSite();
			restartElements();
			// The bundle restart is what would heal; give it the same window, then
			// assert the module stayed away.
			cy.wait(10000);
			siteHasElements().then(has => expect(has, 'deliberate deactivation sticks').to.eq(false));

			// Leave the shared site usable for whoever runs next: the manual gesture,
			// which the one-shot marker never blocks.
			enableModule('formidable-elements', FORMIDABLE_TEST_SITE.key);
			cy.waitUntil(() => siteHasElements(), {timeout: 30000, interval: 1000});
		});
	});
});
