import {FormElement} from './FormElement';

export class HiddenInput extends FormElement {
	/**
	 * Assert that the input is hidden (type="hidden")
	 */
	shouldBeHidden(): this {
		this.getInput().should('have.attr', 'type', 'hidden');
		this.getInput().should('not.be.visible');
		return this;
	}

	/**
	 * Assert the value of the hidden input
	 */
	shouldHaveValue(value: string): this {
		this.getInput().should('have.value', value);
		return this;
	}

	/**
	 * Assert the name attribute of the hidden input
	 */
	shouldHaveName(name: string): this {
		this.getInput().should('have.attr', 'name', name);
		return this;
	}

	/**
	 * Get the value of the hidden input
	 */
	getValue(): Cypress.Chainable<string> {
		return this.getInput().invoke('val').then(val => val as string);
	}
}

