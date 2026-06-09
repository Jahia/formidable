import {FormElement} from './FormElement';

/**
 * File input form element
 * Handles file upload and validation
 */
export class FileInput extends FormElement {
	/**
	 * Attach a file to the input
	 * @param filePath - Path to the file relative to fixtures folder
	 */
	attachFile(filePath: string | string[]): this {
		this.getInput().selectFile(filePath);
		return this;
	}

	/**
	 * Assert that the input accepts specific file extensions
	 */
	shouldAccept(extensions: string | string[]): this {
		const expectedTokens = Array.isArray(extensions)
			? extensions
			: extensions.split(',').map(token => token.trim()).filter(Boolean);

		this.getInput().invoke('attr', 'accept').then((acceptAttr: string) => {
			expectedTokens.forEach(token => {
				expect(acceptAttr).to.contain(token);
			});
		});
		return this;
	}

	/**
	 * Assert that the input allows multiple files
	 */
	shouldBeMultiple(): this {
		this.getInput().should('have.attr', 'multiple');
		return this;
	}

	/**
	 * Assert that the input does not allow multiple files
	 */
	shouldNotBeMultiple(): this {
		this.getInput().should('not.have.attr', 'multiple');
		return this;
	}

	/**
	 * Get the file label element
	 */
	getFileLabel(): Cypress.Chainable {
		return this.getContainer().find('.fmdb-file-label');
	}

	getFileContainer(): Cypress.Chainable {
		return this.getInput().closest('.fmdb-file-input-container');
	}

	getSelectionNotice(): Cypress.Chainable {
		return this.getFileContainer().find('.fmdb-file-selection-note');
	}

	getSelectedFiles(): Cypress.Chainable {
		return this.getFileContainer().find('.fmdb-file-item');
	}

	removeSelectedFile(index: number): this {
		this.getSelectedFiles().eq(index).find('.fmdb-file-remove').click();
		return this;
	}

	shouldHaveSelectionNotice(text: string): this {
		this.getSelectionNotice().should('contain', text);
		return this;
	}

	shouldHaveSelectedFile(fileName: string): this {
		this.getSelectedFiles().should('contain', fileName);
		return this;
	}

	shouldHaveSelectedFileCount(count: number): this {
		this.getSelectedFiles().should('have.length', count);
		return this;
	}
}
