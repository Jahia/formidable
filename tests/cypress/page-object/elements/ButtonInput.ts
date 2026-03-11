import {FormElement} from './FormElement';

/**
 * Button input form element
 * Handles button clicks and validation
 */
export class ButtonInput extends FormElement {
	/**
	 * Click the button
	 */
	click(): this {
		this.getInput().click();
		return this;
	}

	/**
	 * Assert that the button has the expected type
	 */
	shouldHaveType(type: 'button' | 'submit' | 'reset'): this {
		this.getInput().should('have.attr', 'type', type);
		return this;
	}

	/**
	 * Assert that the button has the expected variant class
	 */
	shouldHaveVariant(variant: 'primary' | 'secondary' | 'danger'): this {
		this.getInput().should('have.class', `fmdb-btn-${variant}`);
		return this;
	}

	/**
	 * Assert that the button has the expected text
	 */
	shouldHaveText(text: string): this {
		this.getInput().should('contain', text);
		return this;
	}

	shouldBeVisible(): this {
		this.getInput().should('be.visible');
		return this;
	}
}

