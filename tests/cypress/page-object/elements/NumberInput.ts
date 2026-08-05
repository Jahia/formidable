import {FormElement} from './FormElement';

export class NumberInput extends FormElement {
	type(value: string): this {
		this.getInput().clear();
		this.getInput().type(value);
		return this;
	}

	clear(): this {
		this.getInput().clear();
		return this;
	}

	shouldBeNumberInput(): this {
		this.getInput().should('have.attr', 'type', 'number');
		return this;
	}

	shouldHavePlaceholder(text: string): this {
		this.getInput().should('have.attr', 'placeholder', text);
		return this;
	}

	shouldHaveValue(value: string): this {
		this.getInput().should('have.value', value);
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
}
