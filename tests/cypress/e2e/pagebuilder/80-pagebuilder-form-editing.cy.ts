import {publishAndWaitJobEnding} from '@jahia/cypress';
import {JContent} from '@jahia/jcontent-cypress/dist/page-object/jcontent';
import {FORMIDABLE_TEST_SITE, getInputTextNode, getStepNode} from '../../support/fixtures';
import {createFormNode} from '../../support/fixtures/forms';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from '../support/useFormidableSite';

const openFormInPageBuilder = (formName: string) => {
	const jcontent = JContent.visit(FORMIDABLE_TEST_SITE.key, 'en', `content-folders/contents/${formName}`);
	return jcontent.switchToPageBuilder();
};

/**
 * A form is editable in the jContent Page Builder from the Content Folders, without a
 * page around it: fmdb:form is a main resource with a template of its own. Inside, the
 * authoring model is the one of a form on a page (spec 38): every step stacked, one box
 * per node, the field list offering one create button per accepted type.
 */
describe('Page Builder - 80 Form editing from the Content Folders', () => {
	useFormidableSite();

	it('opens a form in Page Builder through the fmdb:form template', () => {
		const formName = 'pb-template-form';

		createFormNode(formName, 'PB Template Form', [
			getInputTextNode({name: 'firstName', title: 'First name'})
		]).then(() => {
			const pageBuilder = openFormInPageBuilder(formName);

			pageBuilder.iframe().get().find('form.fmdb-form').should('have.attr', 'data-fmdb-edit-mode', 'true');
			pageBuilder.iframe().get().find('input[name="firstName"]').should('be.visible');
		});
	});

	it('offers, on the field list, one create button per type a form accepts', () => {
		const formName = 'pb-create-buttons-form';

		createFormNode(formName, 'PB Create Buttons Form', [
			getInputTextNode({name: 'firstName', title: 'First name'})
		]).then(() => {
			const pageBuilder = openFormInPageBuilder(formName);
			const fieldsModule = pageBuilder.getModule(`${CONTENT_PATH}/${formName}/fields`, false);

			['fmdbmix:formContent', 'fmdbmix:formElement', 'fmdbmix:formStep'].forEach(mixin => {
				fieldsModule.getCreateButtons().get()
					.find(`button[data-sel-role="${mixin}"]`)
					.should('have.length', 1);
			});
		});
	});

	it('responds 404 on the standalone URL of a published form outside the Page Builder', () => {
		const formName = 'pb-standalone-url-form';

		createFormNode(formName, 'PB Standalone URL Form', [
			getInputTextNode({name: 'firstName', title: 'First name'})
		]).then(() => {
			publishAndWaitJobEnding(`${CONTENT_PATH}/${formName}`);

			// The template is technical: it only answers the Page Builder. The live URL a
			// main resource gets must not quietly publish a form outside the pages (and
			// their ACLs) that embed it. The edit-mode 200 first pins the path: without
			// it, a typo would 404 too and prove nothing.
			cy.request(`/cms/editframe/default/en${CONTENT_PATH}/${formName}.html`)
				.its('status').should('eq', 200);
			cy.request({
				url: `/en${CONTENT_PATH}/${formName}.html`,
				failOnStatusCode: false
			}).its('status').should('eq', 404);
		});
	});

	it('stacks every step so all of them stay editable', () => {
		const formName = 'pb-steps-form';

		createFormNode(formName, 'PB Steps Form', [
			getStepNode({
				name: 'stepOne',
				title: 'Step one',
				children: [getInputTextNode({name: 'firstName', title: 'First name'})]
			}),
			getStepNode({
				name: 'stepTwo',
				title: 'Step two',
				children: [getInputTextNode({name: 'lastName', title: 'Last name'})]
			})
		]).then(() => {
			const pageBuilder = openFormInPageBuilder(formName);

			// Steps after the first would be hidden for a visitor; while authoring, all of
			// them are rendered, with their titles, and the second one is reachable.
			pageBuilder.iframe().get().find('[data-fmdb-step]:visible').should('have.length', 2);
			pageBuilder.iframe().get().find('.fmdb-step-title').should('have.length', 2);
			pageBuilder.iframe().get().find('input[name="lastName"]').should('be.visible');
			pageBuilder.iframe().get().find('.fmdb-steps-nav, .fmdb-next-btn').should('not.exist');
		});
	});
});
