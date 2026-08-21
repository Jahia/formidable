import {
	CHECKBOX_GROUP_COMPLETE,
	FORMIDABLE_TEST_SITE,
	getCheckboxNode,
	getInputColorNode,
	getInputDateNode,
	getInputDatetimeLocalNode,
	getInputEmailNode,
	getInputFileNode,
	getInputTextNode,
	getRadioNode,
	getSelectNode,
	getStepNode,
	getTextareaNode,
	INPUT_COLOR_COMPLETE,
	INPUT_DATE_COMPLETE,
	INPUT_DATETIME_LOCAL_COMPLETE,
	INPUT_EMAIL_COMPLETE,
	INPUT_FILE_MULTIPLE,
	INPUT_TEXT_COMPLETE,
	RADIO_GROUP,
	SELECT_SINGLE,
	TEXTAREA_COMPLETE
} from '../../support/fixtures';
import {publishAndWaitJobEnding, setNodeProperty, uploadFile} from '@jahia/cypress';
import {createPublishedLiveFormPage, visitLiveForm, visitPreviewForm} from '../../support/fixtures/forms';
import {SITE_HOME_PATH} from '../../support/constants';
import {useFormidableSite} from './support';
import type {Form} from '../../page-object';

// Form intro authored with the Content Editor picker formats: an internal page
// link (/cms/{mode}/{lang}/...) and an internal image (/files/{workspace}/...).
// The intro travels through the Form island props, so the placeholders must be
// resolved server-side, in every language and in both live and preview modes.
const SITE_FILES_PATH = `/sites/${FORMIDABLE_TEST_SITE.key}/files`;
const homeLink = (label: string) =>
	`<a href="/cms/{mode}/{lang}/sites/${FORMIDABLE_TEST_SITE.key}/home.html">${label}</a>`;
const CATS_IMAGE = `<img src="/files/{workspace}/sites/${FORMIDABLE_TEST_SITE.key}/files/cats.jpg" alt="cats"/>`;
const INTRO_EN = `<p>Welcome! Have a look at ${homeLink('our home page')} before starting. ${CATS_IMAGE}</p>`;
const INTRO_FR = `<p>Bienvenue ! Consultez ${homeLink('notre page d’accueil')} avant de commencer. ${CATS_IMAGE}</p>`;

describe('Form fields - 20 All field types', () => {
	useFormidableSite();

	// The all-fields page is the heaviest render of the suite and its first visit
	// intermittently times out at the socket level on CI (ESOCKETTIMEDOUT): one
	// retry absorbs that warm-up without hiding anything else. CI-only.
	it('submits a simple live form with all supported field types', {retries: {runMode: 1}}, () => {
		createPublishedLiveFormPage(
			'all-fields-simple-form',
			'All Fields Simple Form',
			[
				getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: undefined}),
				getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: undefined}),
				getInputDateNode({...INPUT_DATE_COMPLETE, defaultValue: undefined}),
				getInputDatetimeLocalNode({...INPUT_DATETIME_LOCAL_COMPLETE, defaultValue: undefined}),
				getInputColorNode(INPUT_COLOR_COMPLETE),
				getCheckboxNode(CHECKBOX_GROUP_COMPLETE),
				getRadioNode(RADIO_GROUP),
				getSelectNode(SELECT_SINGLE),
				getTextareaNode({...TEXTAREA_COMPLETE, defaultValue: undefined}),
				getInputFileNode(INPUT_FILE_MULTIPLE)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.getTextInput(INPUT_TEXT_COMPLETE.name!)
				.type('AB-9876')
				.shouldBeValid();
			form.getEmailInput(INPUT_EMAIL_COMPLETE.name!)
				.type('contact@example.com')
				.shouldBeValid();
			form.getDateInput(INPUT_DATE_COMPLETE.name!)
				.setDate('2001-02-03')
				.shouldBeValid();
			form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_COMPLETE.name!)
				.setDateTime('2026-06-12T09:30')
				.shouldBeValid();
			form.getColorInput(INPUT_COLOR_COMPLETE.name!)
				.setColor('#336699')
				.shouldBeValid();
			form.getCheckboxGroup(CHECKBOX_GROUP_COMPLETE.name!)
				.checkByLabels(['Sports']);
			form.getRadioGroup(RADIO_GROUP.name!)
				.select('Pickup')
				.shouldHaveSelected('Pickup');
			form.getSelectInput(SELECT_SINGLE.name!)
				.shouldHaveSelectedOption('Please select')
				.select('Support')
				.shouldHaveSelectedOption('Support');
			form.getTextarea(TEXTAREA_COMPLETE.name!)
				.type('A longer project summary that satisfies the field constraints.')
				.shouldBeValid();
			form.getFileInput(INPUT_FILE_MULTIPLE.name!)
				.attachFile('cypress/fixtures/files/sample.csv')
				.shouldHaveSelectedFile('sample.csv');

			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});

	it('renders a multilingual intro with a resolved internal link and image', () => {
		uploadFile('files/cats.jpg', SITE_FILES_PATH, 'cats.jpg', 'image/jpeg').then(() => {
			publishAndWaitJobEnding(`${SITE_FILES_PATH}/cats.jpg`);
			// The intro links to the site home page: give it a french translation
			// and publish it so its live URL serves in both languages
			setNodeProperty(SITE_HOME_PATH, 'jcr:title', 'Accueil', 'fr');
			publishAndWaitJobEnding(SITE_HOME_PATH, ['en', 'fr']);

			createPublishedLiveFormPage(
				'all-fields-intro-form',
				'All Fields Intro Form',
				[getInputTextNode({name: 'contactName', title: 'Contact name'})],
				undefined,
				undefined,
				{
					properties: [
						{name: 'intro', value: INTRO_EN, language: 'en'},
						{name: 'intro', value: INTRO_FR, language: 'fr'},
						{name: 'jcr:title', value: 'Formulaire avec intro', language: 'fr'}
					],
					pageProperties: [{name: 'jcr:title', value: 'Formulaire avec intro', language: 'fr'}],
					publishLanguages: ['en', 'fr']
				}
			).then(({livePath}) => {
				const assertIntro = (
					label: string,
					pageUrl: string,
					openForm: () => Form,
					welcome: string,
					linkLabel: string
				) => {
					// No unresolved placeholders anywhere, island props included
					cy.request(pageUrl).its('body').should(body => {
						expect(body, `${label} no unresolved placeholders in page source`).to.not.contain('/cms/{mode}');
						expect(body, `${label} no unresolved placeholders in page source`).to.not.contain('{workspace}');
					});

					const form = openForm();
					form.getIntro().should('contain', welcome);

					form.getIntro().find('a')
						.should('contain', linkLabel)
						.invoke('attr', 'href')
						.then(href => {
							expect(href, `${label} intro link is resolved`).to.not.contain('{mode}');
							expect(href, `${label} intro link is resolved`).to.not.contain('{lang}');
							expect(href, `${label} intro link is resolved`).to.not.contain('##');
							cy.request({url: href, retryOnStatusCodeFailure: true}).its('status').should('eq', 200);
						});

					form.getIntro().find('img')
						.invoke('attr', 'src')
						.then(src => {
							expect(src, `${label} intro image is resolved`).to.not.contain('{workspace}');
							expect(src, `${label} intro image is resolved`).to.not.contain('##');
							expect(src, `${label} intro image targets the media`).to.contain('cats.jpg');
							cy.request({url: src, retryOnStatusCodeFailure: true}).its('status').should('eq', 200);
						});
				};

				const liveUrl = (lang: string) => `/${lang}/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`;
				const previewUrl = (lang: string) => `/cms/render/default/${lang}/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`;

				assertIntro('[en live]', liveUrl('en'), () => visitLiveForm(livePath, 'en'), 'Welcome!', 'our home page');
				assertIntro('[fr live]', liveUrl('fr'), () => visitLiveForm(livePath, 'fr'), 'Bienvenue !', 'notre page d’accueil');

				// Preview mode rewrites the same stored URLs differently (render/default servlet)
				assertIntro('[en preview]', previewUrl('en'), () => visitPreviewForm(livePath, 'en'), 'Welcome!', 'our home page');
				assertIntro('[fr preview]', previewUrl('fr'), () => visitPreviewForm(livePath, 'fr'), 'Bienvenue !', 'notre page d’accueil');
			});
		});
	});

	it('submits a multistep live form with all supported field types distributed across steps', () => {
		createPublishedLiveFormPage(
			'all-fields-step-form',
			'All Fields Step Form',
			[
				getStepNode({
					name: 'identityStep',
					title: 'Identity',
					label: 'Identity',
					children: [
						getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: undefined}),
						getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: undefined}),
						getCheckboxNode(CHECKBOX_GROUP_COMPLETE),
						getRadioNode(RADIO_GROUP),
						getSelectNode(SELECT_SINGLE)
					]
				}),
				getStepNode({
					name: 'detailsStep',
					title: 'Details',
					label: 'Details',
					children: [
						getInputDateNode({...INPUT_DATE_COMPLETE, defaultValue: undefined}),
						getInputDatetimeLocalNode({...INPUT_DATETIME_LOCAL_COMPLETE, defaultValue: undefined}),
						getInputColorNode(INPUT_COLOR_COMPLETE),
						getTextareaNode({...TEXTAREA_COMPLETE, defaultValue: undefined}),
						getInputFileNode(INPUT_FILE_MULTIPLE)
					]
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.shouldHaveVisibleStepCount(2).shouldHaveCurrentStep('Identity');
			form.getTextInput(INPUT_TEXT_COMPLETE.name!).type('CD-4567');
			form.getEmailInput(INPUT_EMAIL_COMPLETE.name!).type('step@example.com');
			form.getCheckboxGroup(CHECKBOX_GROUP_COMPLETE.name!).checkByLabels(['Sports']);
			form.getRadioGroup(RADIO_GROUP.name!).select('Express');
			form.getSelectInput(SELECT_SINGLE.name!)
				.shouldHaveSelectedOption('Please select')
				.select('Sales');
			form.nextStep();

			form.shouldHaveCurrentStep('Details');
			form.getDateInput(INPUT_DATE_COMPLETE.name!).setDate('2004-04-05');
			form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_COMPLETE.name!).setDateTime('2026-06-20T14:15');
			form.getColorInput(INPUT_COLOR_COMPLETE.name!).setColor('#663399');
			form.getTextarea(TEXTAREA_COMPLETE.name!).type('Second step details long enough for textarea validation.');
			form.getFileInput(INPUT_FILE_MULTIPLE.name!).attachFile('cypress/fixtures/files/document.pdf');

			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});
});
