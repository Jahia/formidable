import {JContent} from '@jahia/jcontent-cypress/dist/page-object/jcontent';
import {
	FORMIDABLE_TEST_SITE,
	getInputTextNode,
	getSelectNode,
	getStepNode
} from '../../support/fixtures';
import {createFormNode} from '../../support/fixtures/forms';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from '../support/useFormidableSite';

const openFormInPageBuilder = (formName: string) => {
	const jcontent = JContent.visit(
		FORMIDABLE_TEST_SITE.key,
		'en',
		`content-folders/contents/${formName}`
	);

	return jcontent.switchToPageBuilder();
};

describe('Page Builder - 70 Form editing', () => {
	useFormidableSite();

	it('opens a form in Page Builder through the fmdb:form template', () => {
		const formName = 'pb-template-form';

		createFormNode(formName, 'PB Template Form', [
			getInputTextNode({name: 'firstName', title: 'First name'})
		]).then(() => {
			const pageBuilder = openFormInPageBuilder(formName);

			pageBuilder.iframe().get().find('form.fmdb-form').should('exist');
			pageBuilder.iframe().get().find('input[name="firstName"]').should('be.visible');
		});
	});

	it('shows a single create button on the field list to add form elements in place', () => {
		const formName = 'pb-create-buttons-form';

		createFormNode(formName, 'PB Create Buttons Form', [
			getInputTextNode({name: 'firstName', title: 'First name'})
		]).then(() => {
			const pageBuilder = openFormInPageBuilder(formName);
			const fieldsModule = pageBuilder.getModule(`${CONTENT_PATH}/${formName}/fields`, false);

			// One "New content" button backed by the fmdbmix:formItem umbrella type;
			// the concrete choices are offered by the content type picker it opens.
			fieldsModule.getCreateButtons().get()
				.find('button[data-sel-role="fmdbmix:formItem"]')
				.should('have.length', 1);
		});
	});

	it('keeps all steps and logic-hidden fields visible so they stay editable', () => {
		const formName = 'pb-hidden-elements-form';
		const logicRule = JSON.stringify({
			sourceFieldName: 'role',
			sourceFieldType: 'fmdb:select',
			operator: 'in',
			values: ['admin']
		});
		const logicHiddenField = getInputTextNode({name: 'adminCode', title: 'Admin code'});
		logicHiddenField.properties.push({name: 'logics', values: [logicRule]});

		createFormNode(formName, 'PB Hidden Elements Form', [
			getStepNode({
				name: 'step-one',
				title: 'Step one',
				children: [
					getSelectNode({
						name: 'role',
						title: 'Role',
						options: [
							{value: 'admin', label: 'admin', selected: false},
							{value: 'viewer', label: 'viewer', selected: false}
						]
					}),
					logicHiddenField
				]
			}),
			getStepNode({
				name: 'step-two',
				title: 'Step two',
				children: [getInputTextNode({name: 'lastName', title: 'Last name'})]
			})
		]).then(() => {
			const pageBuilder = openFormInPageBuilder(formName);

			// The logic-hidden field would be display:none in live; it must stay
			// visible in Page Builder so contributors can select and edit it.
			pageBuilder.iframe().get().find('input[name="adminCode"]').should('be.visible');

			// Steps after the first would be hidden in live; in Page Builder all
			// steps are rendered stacked and stay editable.
			pageBuilder.iframe().get().find('input[name="lastName"]').should('be.visible');
		});
	});
});
