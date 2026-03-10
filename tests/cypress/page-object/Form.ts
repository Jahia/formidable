import {BaseComponent} from '@jahia/cypress';
import {Fieldset} from './Fieldset';
import {TextInput} from './elements/TextInput';
import {EmailInput} from './elements/EmailInput';
import {DateInput} from './elements/DateInput';
import {CheckboxInput} from './elements/CheckboxInput';
import {CheckboxGroup} from './elements/CheckboxGroup';
import {RadioGroup} from './elements/RadioGroup';
import {SelectInput} from './elements/SelectInput';
import {TextareaInput} from './elements/TextareaInput';
import {FileInput} from './elements/FileInput';
import {ColorInput} from './elements/ColorInput';
import {DateTimeLocalInput} from './elements/DateTimeLocalInput';
import {ButtonInput} from "./elements/ButtonInput";
import {HiddenInput} from "./elements/HiddenInput";

/**
 * Form component - Main form container
 * Corresponds to fmdb:form node type
 */
export class Form extends BaseComponent {
	/**
	 * Get the form intro content
	 */
	getIntro(): Cypress.Chainable {
		return this.get().find('header.fmdb-form-intro');
	}

	/**
	 * Get a fieldset by its legend text
	 */
	getFieldset(legend: string): Fieldset {
		return new Fieldset(
			this.get().find('fieldset.fmdb-fieldset').filter(`:has(legend:contains("${legend}"))`).first()
		);
	}

	/**
	 * Get a text input directly in the form (not in a fieldset) by its name attribute
	 */
	getTextInput(name: string): TextInput {
		return new TextInput(
			this.get().find(`> jsm-children > .fmdb-form-group input[type="text"][name="${name}"]`)
		);
	}

	/**
	 * Get an email input directly in the form by its name attribute
	 */
	getEmailInput(name: string): EmailInput {
		return new EmailInput(
			this.get().find(`> jsm-children > .fmdb-form-group input[type="email"][name="${name}"]`)
		);
	}

	/**
	 * Get a date input directly in the form by its name attribute
	 */
	getDateInput(name: string): DateInput {
		return new DateInput(
			this.get().find(`> jsm-children > .fmdb-form-group input[type="date"][name="${name}"]`)
		);
	}

	/**
	 * Get a datetime-local input directly in the form by its name attribute
	 */
	getDateTimeLocalInput(name: string): DateTimeLocalInput {
		return new DateTimeLocalInput(
			this.get().find(`> jsm-children > .fmdb-form-group input[type="datetime-local"][name="${name}"]`)
		);
	}

	/**
	 * Get a color input directly in the form by its name attribute
	 */
	getColorInput(name: string): ColorInput {
		return new ColorInput(
			this.get().find(`> jsm-children > .fmdb-form-group input[type="color"][name="${name}"]`)
		);
	}

	/**
	 * Get a single checkbox directly in the form by its name attribute
	 */
	getCheckbox(name: string): CheckboxInput {
		return new CheckboxInput(
			this.get().find(`> jsm-children > .fmdb-form-group input[type="checkbox"][name="${name}"]`)
		);
	}

	/**
	 * Get a checkbox group directly in the form by its name attribute
	 */
	getCheckboxGroup(name: string): CheckboxGroup {
		return new CheckboxGroup(
			this.get().find(`> jsm-children fieldset.fmdb-checkbox-group:has(input[name="${name}"])`).first()
		);
	}

	/**
	 * Get a radio group directly in the form by its name attribute
	 */
	getRadioGroup(name: string): RadioGroup {
		return new RadioGroup(
			this.get().find(`> jsm-children fieldset.fmdb-radio-group:has(input[name="${name}"])`).first()
		);
	}

	/**
	 * Get a select input directly in the form by its name attribute
	 */
	getSelectInput(name: string): SelectInput {
		return new SelectInput(
			this.get().find(`> jsm-children > .fmdb-form-group select[name="${name}"]`)
		);
	}

	/**
	 * Get a textarea directly in the form by its name attribute
	 */
	getTextarea(name: string): TextareaInput {
		return new TextareaInput(
			this.get().find(`> jsm-children > .fmdb-form-group textarea[name="${name}"]`)
		);
	}

	/**
	 * Get a file input directly in the form by its name attribute
	 */
	getFileInput(name: string): FileInput {
		return new FileInput(
			this.get().find(`> jsm-children > .fmdb-form-group input[type="file"][name="${name}"]`)
		);
	}

	/**
	 * Get a button by its name attribute or text content
	 */
	getButton(identifier: string): ButtonInput {
		return new ButtonInput(
			this.get().find(`> jsm-children button:contains("${identifier}"), > jsm-children button[name="${identifier}"]`).first()
		);
	}

	/**
	 * Get a hidden input directly in the form by its name attribute
	 */
	getHiddenInput(name: string): HiddenInput {
		return new HiddenInput(
			this.get().find(`> jsm-children input[type="hidden"][name="${name}"]`)
		);
	}


	/**
	 * Get the submit button
	 */
	getSubmitButton(): ButtonInput {
		return new ButtonInput(
			this.get().find('button[type="submit"]')
		);
	}

	/**
	 * Get the reset button
	 */
	getResetButton(): ButtonInput {
		return new ButtonInput(
			this.get().find('button[type="reset"]')
		);
	}

	/**
	 * Submit the form
	 */
	submit(): void {
		this.getSubmitButton().get().click();
	}

	/**
	 * Reset the form
	 */
	reset(): void {
		this.getResetButton().get().click();
	}

	shouldExist(): this {
		this.get().should('exist');
		return this;
	}

	shouldBeVisible(): this {
		this.get().should('be.visible');
		return this;
	}

	shouldHaveIntro(text: string): this {
		this.getIntro().should('contain', text);
		return this;
	}

	shouldHaveSubmissionMessage(text: string): this {
		this.get().find('.fmdb-form-submission').should('contain', text);
		return this;
	}

	shouldHaveErrorMessage(text: string): this {
		this.get().find('.fmdb-form-error').should('contain', text);
		return this;
	}

	waitForSubmit(): this {
		this.get().find('.fmdb-form-submission, .fmdb-form-error').should('be.visible');
		return this;
	}
}
