import {FormElement} from './FormElement';

/**
 * Textarea form element
 * Handles multi-line text input with typing, clearing, and validation
 */
export class TextareaInput extends FormElement {
	/**
	 * Type a value into the textarea
	 */
	type(value: string): this {
		// Directly set value using invoke - faster and more reliable than .type()
		this.getInput().invoke('val', value);
		return this;
	}

	/**
	 * Clear the textarea
	 */
	clear(): this {
		this.getInput().invoke('val', '');
		return this;
	}

	/**
	 * Assert that the textarea has the expected placeholder text
	 */
	shouldHavePlaceholder(text: string): this {
		this.getInput().should('have.attr', 'placeholder', text);
		return this;
	}

	/**
	 * Assert that the textarea has the expected number of rows
	 */
	shouldHaveRows(rows: number): this {
		this.getInput().should('have.attr', 'rows', rows.toString());
		return this;
	}

	/**
	 * Assert that the textarea has the expected value
	 */
	shouldHaveValue(value: string): this {
		this.getInput().should('have.value', value);
		return this;
	}

	/**
	 * Assert that the textarea has the expected wrap attribute
	 */
	shouldHaveWrap(wrap: 'soft' | 'hard' | 'off'): this {
		this.getInput().should('have.attr', 'wrap', wrap);
		return this;
	}

	shouldHaveMinLength(length: number): this {
		this.getInput().should('have.attr', 'minlength', length.toString());
		return this;
	}

	shouldHaveMaxLength(length: number): this {
		this.getInput().should('have.attr', 'maxlength', length.toString());
		return this;
	}

	shouldHaveResize(resize: 'none' | 'both' | 'horizontal' | 'vertical'): this {
		this.getInput().should('have.css', 'resize', resize);
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

	shouldHaveAutocomplete(value: string): this {
		this.getInput().should('have.attr', 'autocomplete', value);
		return this;
	}


	shouldHaveSpellcheck(value: boolean): this {
		this.getInput().should('have.attr', 'spellcheck', value.toString());
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

	shouldHaveCols(cols: number): this {
		this.getInput().should('have.attr', 'cols', cols.toString());
		return this;
	}
}
