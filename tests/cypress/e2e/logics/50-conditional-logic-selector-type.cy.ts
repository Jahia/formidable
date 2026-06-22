import {ConditionalLogicEditor} from '../../page-object';
import {createConditionalLogicForm} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';

describe('Form logic - 50 Conditional logic selector type', () => {
	useFormidableSite();

	it('lists only supported prior source fields and adapts operators by source type', () => {
		createConditionalLogicForm(`${Date.now()}-sources`).then(({targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();

			logicField.openDropdown(0, 0);
			logicField.menuShouldHaveItems(['role', 'accept-terms', 'start-date']);
			logicField.menuShouldNotHaveItems(['notes', 'nickname']);
			logicField.selectMenuItem('role');

			logicField.openDropdown(0, 1);
			logicField.menuShouldHaveItems(['is one of', 'is not one of']);
			logicField.selectMenuItem('is one of');
			logicField.ruleShouldHaveDropdownCount(0, 3);

			logicField.selectSource(0, 'accept-terms');
			logicField.openDropdown(0, 1);
			logicField.menuShouldHaveItems(['is checked', 'is unchecked']);
			logicField.selectMenuItem('is checked');
			logicField.ruleShouldHaveDropdownCount(0, 2);

			logicField.selectSource(0, 'start-date');
			logicField.openDropdown(0, 1);
			logicField.menuShouldHaveItems(['is before', 'is after', 'is on', 'is between']);
			logicField.selectMenuItem('is after');
			logicField.ruleShouldHaveDateInputCount(0, 1);

			logicField.selectOperator(0, 'is between');
			logicField.ruleShouldHaveDateInputCount(0, 2);

			editor.cancelAndDiscard();
		});
	});

	it('excludes already used source nodes from sibling rules and reloads saved rules', () => {
		createConditionalLogicForm(`${Date.now()}-reopen`).then(({targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.selectSource(0, 'role');
			logicField.selectValue(0, 'admin');

			logicField.addRule();
			logicField.openDropdown(1, 0);
			logicField.menuShouldHaveItems(['accept-terms', 'start-date']);
			logicField.menuShouldNotHaveItems(['role', 'notes', 'nickname']);
			logicField.selectMenuItem('accept-terms');

			editor.save();
			editor.cancel();

			const reopenedEditor = ConditionalLogicEditor.visit(targetPath);
			reopenedEditor.logicField.ruleShouldContainText(0, 'role');
			reopenedEditor.logicField.ruleShouldContainText(0, 'is one of');
			reopenedEditor.logicField.ruleShouldContainText(0, 'admin');
		});
	});
});
