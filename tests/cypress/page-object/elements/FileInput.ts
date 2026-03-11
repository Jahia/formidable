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
	attachFile(filePath: string): this {
		this.getInput().selectFile(filePath);
		return this;
	}

	/**
	 * Assert that the input accepts specific file extensions
	 */
	shouldAccept(extensions: string): this {
		this.getInput().should('have.attr', 'accept', extensions);
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
		return this.get().parent('.fmdb-form-group').find('.fmdb-file-label');
	}
}
