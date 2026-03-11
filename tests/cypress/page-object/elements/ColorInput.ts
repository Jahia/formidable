import {FormElement} from './FormElement';

export class ColorInput extends FormElement {
	setColor(color: string): this {
		this.getInput().invoke('val', color).trigger('input');
		return this;
	}

	shouldBeColorInput(): this {
		this.getInput().should('have.attr', 'type', 'color');
		return this;
	}

	shouldHaveValue(color: string): this {
		this.getInput().should('have.value', color);
		return this;
	}
}
