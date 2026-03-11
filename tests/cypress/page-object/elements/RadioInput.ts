import {FormElement} from './FormElement';

/**
 * Radio button form element
 * Note: Radio inputs should typically be used within a RadioGroup
 * This class provides basic radio button functionality
 */
export class RadioInput extends FormElement {
	/**
	 * Select this radio button
	 */
	select(): this {
		this.getInput().check();
		return this;
	}

	/**
	 * Assert that this radio is selected
	 */
	shouldBeSelected(): this {
		this.getInput().should('be.checked');
		return this;
	}

	/**
	 * Assert that this radio is not selected
	 */
	shouldNotBeSelected(): this {
		this.getInput().should('not.be.checked');
		return this;
	}

	/**
	 * Assert that this radio has the expected value
	 */
	shouldHaveValue(value: string): this {
		this.getInput().should('have.value', value);
		return this;
	}

	/**
	 * Assert that this radio is default checked
	 */
	shouldBeDefaultChecked(): this {
		this.getInput().should('have.prop', 'defaultChecked', true);
		return this;
	}
}

