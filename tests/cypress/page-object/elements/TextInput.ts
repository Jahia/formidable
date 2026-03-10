import {FormElement} from './FormElement';

/**
 * Text input form element
 * Handles single-line text inputs with typing, clearing, and validation
 */
export class TextInput extends FormElement {
	type(value: string): this {
		// Directly set value using invoke - faster and more reliable than .type()
		this.getInput().invoke('val', value);
		return this;
	}

	clear(): this {
		this.getInput().invoke('val', '');
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

	shouldHaveMaxLength(length: number): this {
		this.getInput().should('have.attr', 'maxlength', length.toString());
		return this;
	}

	shouldHaveMinLength(length: number): this {
		this.getInput().should('have.attr', 'minlength', length.toString());
		return this;
	}

	shouldHavePattern(pattern: string): this {
		this.getInput().should('have.attr', 'pattern', pattern);
		return this;
	}

	shouldHaveTitle(title: string): this {
		this.getInput().should('have.attr', 'title', title);
		return this;
	}

	shouldHaveAutocomplete(value: string): this {
		this.getInput().should('have.attr', 'autocomplete', value);
		return this;
	}


	shouldBeReadonly(): this {
		this.getInput().should('have.attr', 'readonly');
		return this;
	}

	shouldNotBeReadonly(): this {
		this.getInput().should('not.have.attr', 'readonly');
		return this;
	}

	shouldHaveAutofocus(): this {
		this.getInput().should('have.attr', 'autofocus');
		return this;
	}

	shouldBeDisabled(): this {
		this.getInput().should('be.disabled');
		return this;
	}

	shouldBeEnabled(): this {
		this.getInput().should('be.enabled');
		return this;
	}

	shouldHaveSpellcheck(value: boolean): this {
		this.getInput().should('have.attr', 'spellcheck', value.toString());
		return this;
	}

	shouldHaveSize(size: number): this {
		this.getInput().should('have.attr', 'size', size.toString());
		return this;
	}

	shouldHaveList(listId: string): this {
		this.getInput().should('have.attr', 'list', listId);
		return this;
	}

	shouldHaveMask(): this {
		this.getInput().should('have.attr', 'data-mask');
		return this;
	}
}

