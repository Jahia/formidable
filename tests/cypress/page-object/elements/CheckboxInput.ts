import {FormElement} from './FormElement';

export class CheckboxInput extends FormElement {
	/**
	 * Override getLabel to handle checkbox-specific label association
	 */
	getLabel(): Cypress.Chainable {
		return this.getInput()
			.siblings('label.fmdb-checkbox-label');
	}

	check(): this {
		this.getInput().check();
		return this;
	}

	uncheck(): this {
		this.getInput().uncheck();
		return this;
	}

	shouldBeChecked(): this {
		this.getInput().should('be.checked');
		return this;
	}

	shouldBeUnchecked(): this {
		this.getInput().should('not.be.checked');
		return this;
	}

	shouldHaveValue(value: string): this {
		this.getInput().should('have.value', value);
		return this;
	}

	shouldBeDefaultChecked(): this {
		this.getInput().should('have.attr', 'checked');
		return this;
	}

	shouldBeRequired(): this {
		this.getInput().should('have.attr', 'required');
		this.getLabel().find('.fmdb-required-indicator').should('exist');
		return this;
	}

	shouldNotBeRequired(): this {
		this.getInput().should('not.have.attr', 'required');
		this.getLabel().find('.fmdb-required-indicator').should('not.exist');
		return this;
	}
}
