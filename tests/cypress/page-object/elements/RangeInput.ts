import {FormElement} from './FormElement';

/**
 * Range slider form element. The visible slider is unnamed; the submitted value
 * lives in a hidden input mirrored by the client island (empty until the visitor
 * interacts), so answered-state assertions go through the hidden input.
 */
export class RangeInput extends FormElement {
	getHiddenInput(): Cypress.Chainable {
		return this.getContainer().find('input[type="hidden"]');
	}

	getOutput(): Cypress.Chainable {
		return this.getContainer().find('output.fmdb-range-output');
	}

	/**
	 * Set the slider the way a visitor would: assign the value through the native
	 * setter (so the React island's controlled value tracking sees the change) and
	 * fire a bubbling input event.
	 */
	setValue(value: string): this {
		this.getInput().then($input => {
			const input = $input.get(0) as HTMLInputElement;
			const win = input.ownerDocument.defaultView!;
			const setter = Object.getOwnPropertyDescriptor(win.HTMLInputElement.prototype, 'value')!.set!;
			setter.call(input, value);
			input.dispatchEvent(new Event('input', {bubbles: true}));
		});
		return this;
	}

	shouldBeRangeInput(): this {
		this.getInput().should('have.attr', 'type', 'range');
		return this;
	}

	/**
	 * Retry-able hydration gate: the island arms the required constraint (through
	 * setCustomValidity) only once hydrated, so waiting for the slider to report
	 * invalid guarantees later interactions are picked up by the React island.
	 */
	waitUntilRequiredArmed(): this {
		this.getInput().should($el => {
			expect(($el.get(0) as HTMLInputElement).checkValidity()).to.equal(false);
		});
		return this;
	}

	shouldBeUnanswered(): this {
		this.getHiddenInput().should('have.value', '');
		this.getOutput().should('contain.text', '–');
		return this;
	}

	shouldBeAnswered(value: string): this {
		this.getHiddenInput().should('have.value', value);
		this.getOutput().should('have.text', value);
		return this;
	}

	shouldHaveEndLabels(minLabel: string, maxLabel: string): this {
		this.getContainer().find('.fmdb-range-end-label').first().should('have.text', minLabel);
		this.getContainer().find('.fmdb-range-end-label').last().should('have.text', maxLabel);
		return this;
	}

	// Numeric attribute assertions compare as numbers: a stored double may
	// serialize as "1" or "1.0" depending on the JCR value formatting.
	shouldHaveMin(min: number): this {
		this.getInput().invoke('attr', 'min').then(attr => {
			expect(parseFloat(attr ?? '')).to.equal(min);
		});
		return this;
	}

	shouldHaveMax(max: number): this {
		this.getInput().invoke('attr', 'max').then(attr => {
			expect(parseFloat(attr ?? '')).to.equal(max);
		});
		return this;
	}

	shouldHaveStep(step: number): this {
		this.getInput().invoke('attr', 'step').then(attr => {
			expect(parseFloat(attr ?? '')).to.equal(step);
		});
		return this;
	}

	shouldHaveDatalist(): this {
		this.getInput().should('have.attr', 'list');
		return this;
	}

	shouldHaveDatalistOptions(options: string[]): this {
		this.getInput().invoke('attr', 'list').then((listId: string) => {
			if (listId) {
				this.getContainer().find(`datalist#${listId}`).should('exist');
				options.forEach(option => {
					this.getContainer().find(`datalist#${listId} option[value="${option}"]`).should('exist');
				});
			}
		});
		return this;
	}
}
