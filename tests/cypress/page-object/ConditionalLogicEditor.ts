import {ContentEditor} from '@jahia/jcontent-cypress/dist/page-object';
import {FORMIDABLE_TEST_SITE} from '../support/fixtures/site';
import {ConditionalLogicField} from './ConditionalLogicField';

export class ConditionalLogicEditor {
	constructor(private readonly contentEditor: ContentEditor) {}

	static visit(targetPath: string): ConditionalLogicEditor {
		const contentEditor = ContentEditor.visit(targetPath, FORMIDABLE_TEST_SITE.key, 'en', 'content-folders/contents');
		contentEditor.openSection('logic');

		return new ConditionalLogicEditor(contentEditor);
	}

	get logicField(): ConditionalLogicField {
		const field = new ConditionalLogicField(
			cy.get(ConditionalLogicField.defaultSelector, {timeout: 15000}).should('be.visible')
		);
		field.waitUntilReady();
		return field;
	}

	save(): void {
		this.contentEditor.save();
	}

	cancel(): void {
		this.contentEditor.cancel();
	}

	cancelAndDiscard(): void {
		this.contentEditor.cancelAndDiscard();
	}
}
