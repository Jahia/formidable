import {FormElement} from './FormElement';

export class DateTimeLocalInput extends FormElement {
	setDateTime(datetime: string): this {
		this.getInput().invoke('val', datetime).trigger('change');
		return this;
	}

	shouldBeDateTimeLocalInput(): this {
		this.getInput().should('have.attr', 'type', 'datetime-local');
		return this;
	}

	shouldHaveValue(isoDatetime: string): this {
		// Convert UTC datetime to local datetime for comparison
		const utcDate = new Date(isoDatetime);
		const localDatetime = this.formatLocalDatetime(utcDate);
		this.getInput().should('have.value', localDatetime);
		return this;
	}

	shouldHaveMin(isoMin: string): this {
		// Convert UTC datetime to local datetime for comparison
		const utcDate = new Date(isoMin);
		const localMin = this.formatLocalDatetime(utcDate);
		this.getInput().should('have.attr', 'min', localMin);
		return this;
	}

	shouldHaveMax(isoMax: string): this {
		// Convert UTC datetime to local datetime for comparison
		const utcDate = new Date(isoMax);
		const localMax = this.formatLocalDatetime(utcDate);
		this.getInput().should('have.attr', 'max', localMax);
		return this;
	}

	shouldHaveStep(step: number): this {
		this.getInput().should('have.attr', 'step', step.toString());
		return this;
	}

	private formatLocalDatetime(date: Date): string {
		// Format date to datetime-local format (YYYY-MM-DDTHH:mm)
		const year = date.getFullYear();
		const month = String(date.getMonth() + 1).padStart(2, '0');
		const day = String(date.getDate()).padStart(2, '0');
		const hours = String(date.getHours()).padStart(2, '0');
		const minutes = String(date.getMinutes()).padStart(2, '0');
		return `${year}-${month}-${day}T${hours}:${minutes}`;
	}
}
