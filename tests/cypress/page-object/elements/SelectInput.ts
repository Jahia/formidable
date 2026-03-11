import {FormElement} from './FormElement';

/**
 * Select dropdown form element
 * Handles option selection and validation
 */
export class SelectInput extends FormElement {
	/**
	 * Select an option by its visible text
	 */
	select(option: string): this {
		this.getInput().select(option);
		return this;
	}

	/**
	 * Select an option by its index (0-based)
	 */
	selectByIndex(index: number): this {
		this.getInput().select(index);
		return this;
	}

	/**
	 * Select an option by its value attribute
	 */
	selectByValue(value: string): this {
		this.getInput().select(value);
		return this;
	}

	/**
	 * Get all option elements
	 */
	getOptions(): Cypress.Chainable {
		return this.getInput().find('option');
	}

	/**
	 * Assert that an option with the given text exists
	 */
	shouldHaveOption(option: string): this {
		this.getOptions().should('contain', option);
		return this;
	}

	/**
	 * Assert that the select has the expected number of options
	 */
	shouldHaveOptionCount(count: number): this {
		this.getOptions().should('have.length', count);
		return this;
	}

	/**
	 * Assert that the selected option has the expected text
	 */
	shouldHaveSelectedOption(option: string): this {
		this.getInput().find('option:selected').should('have.text', option);
		return this;
	}

	shouldHaveSize(size: number): this {
		this.getInput().should('have.attr', 'size', size.toString());
		return this;
	}

	shouldBeMultiple(): this {
		this.getInput().should('have.attr', 'multiple');
		return this;
	}

	shouldNotBeMultiple(): this {
		this.getInput().should('not.have.attr', 'multiple');
		return this;
	}

	shouldBeDisabled(): this {
		this.getInput().should('be.disabled');
		return this;
	}

	shouldBeEnabled(): this {
		this.getInput().should('be.enabled');
		return this;
	}

	shouldHaveAutofocus(): this {
		this.getInput().should('have.attr', 'autofocus');
		return this;
	}
}
