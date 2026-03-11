import {FormElement} from './FormElement';

export class DateInput extends FormElement {
	setDate(date: string): this {
		this.getInput().invoke('val', date).trigger('change');
		return this;
	}

	shouldBeDateInput(): this {
		this.getInput().should('have.attr', 'type', 'date');
		return this;
	}

	shouldHaveValue(isoDate: string): this {
		// Parse ISO date and extract only the date part
		const date = isoDate.slice(0, 10);
		this.getInput().should('have.value', date);
		return this;
	}

	shouldHaveMin(isoMin: string): this {
		const min = isoMin.slice(0, 10);
		this.getInput().should('have.attr', 'min', min);
		return this;
	}

	shouldHaveMax(isoMax: string): this {
		const max = isoMax.slice(0, 10);
		this.getInput().should('have.attr', 'max', max);
		return this;
	}

	shouldHaveStep(step: number): this {
		this.getInput().should('have.attr', 'step', step.toString());
		return this;
	}


}
