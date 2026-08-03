import {getNodeByPath, publishAndWaitJobEnding} from '@jahia/cypress';
import {getConsentNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {SITE_HOME_PATH} from '../../support/constants';
import {useFormidableSite} from './support';

interface NodeByPathResponse {
	data?: {
		jcr?: {
			nodeByPath?: {uuid?: string} | null;
		} | null;
	};
}

describe('Form fields - 215 Consent (extended-inputs)', () => {
	useFormidableSite();

	it('renders the rich-text statement as a required checkbox and checks it', () => {
		createPublishedLiveFormPage(
			'consent-basic-form',
			'Consent Basic Form',
			[getConsentNode({
				name: 'gdpr',
				statement: '<p>I accept the <strong>terms of service</strong></p>',
				helpText: '<p>Why we ask for consent</p>'
			})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('.fmdbext-consent').as('consent').should('be.visible');

			// required defaults to true in the CND (autocreated).
			cy.get('@consent').find('input[name="gdpr"]')
				.should('have.attr', 'type', 'checkbox')
				.and('have.value', 'true')
				.and('have.attr', 'required');
			cy.get('@consent').find('.fmdb-required-indicator').should('exist');

			// The statement is contributor-authored rich text, rendered as HTML.
			cy.get('@consent').find('.fmdbext-consent-statement strong')
				.should('contain.text', 'terms of service');

			// Consent supports help text like the other fields, linked via aria-describedby.
			cy.get('@consent').find('.fmdb-form-help').should('contain.text', 'Why we ask for consent');
			cy.get('@consent').find('input[name="gdpr"]').should('have.attr', 'aria-describedby');

			cy.get('@consent').find('input[name="gdpr"]').check({force: true});
			cy.get('@consent').find('input[name="gdpr"]').should('be.checked');
		});
	});

	it('links to the referenced terms page in a new tab', () => {
		// The terms target must be visible to live visitors, otherwise the view
		// skips the link entirely (documented CND constraint) — publish it first.
		publishAndWaitJobEnding(SITE_HOME_PATH);

		getNodeByPath(SITE_HOME_PATH).then((response: NodeByPathResponse) => {
			const homeUuid = response.data?.jcr?.nodeByPath?.uuid;
			expect(homeUuid, `UUID of ${SITE_HOME_PATH}`).to.be.a('string').and.not.be.empty;

			createPublishedLiveFormPage(
				'consent-terms-form',
				'Consent Terms Form',
				[getConsentNode({
					name: 'gdpr-terms',
					statement: '<p>I accept the terms</p>',
					termsTargetUuid: homeUuid,
					termsLinkLabel: 'Read our terms'
				})]
			).then(({livePath}) => {
				const form = visitLiveForm(livePath);

				form.get().find('.fmdbext-consent a.fmdbext-consent-terms-link')
					.should('contain.text', 'Read our terms')
					.and('have.attr', 'target', '_blank')
					.and('have.attr', 'href')
					.and('include', '/home.html');
			});
		});
	});
});
