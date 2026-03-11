import {BaseComponent} from '@jahia/cypress';
import {FormElement} from './FormElement';

/**
 * Radio button within a radio group
 */
class RadioInput extends FormElement {
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
	 * Assert that this radio has the expected value
	 */
	shouldHaveValue(value: string): this {
		this.getInput().should('have.value', value);
		return this;
	}
}

/**
 * Radio group form element
 * Handles mutually exclusive radio button options
 */
export class RadioGroup extends BaseComponent {
	/**
	 * Get the legend element for this radio group
	 */
	getLegend(): Cypress.Chainable {
		return this.get().find('legend.fmdb-group-legend');
	}

	/**
	 * Get a specific radio button by its label text
	 */
	getRadio(label: string): RadioInput {
		return new RadioInput(
			this.get()
				.find('.fmdb-group-items .fmdb-group-item')
				.contains('label', label)
				.prev('input[type="radio"]')
		);
	}

	/**
	 * Select a radio button by its label text
	 */
	select(label: string): this {
		this.getRadio(label).select();
		return this;
	}

	/**
	 * Assert that a specific radio is selected
	 */
	shouldHaveSelected(label: string): this {
		this.getRadio(label).shouldBeSelected();
		return this;
	}

	/**
	 * Assert that the legend contains the expected text
	 */
	shouldHaveLegend(text: string): this {
		this.getLegend().should('contain', text);
		return this;
	}

	/**
	 * Assert that the group is marked as required
	 */
	shouldBeRequired(): this {
		this.getLegend().find('.fmdb-required-indicator').should('exist');
		return this;
	}

	shouldBeVisible(): this {
		this.get().should('be.visible');
		return this;
	}
}
