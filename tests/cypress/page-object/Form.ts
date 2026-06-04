import {BaseComponent} from '@jahia/cypress';
import {Fieldset} from './Fieldset';
import {TextInput} from './elements/TextInput';
import {EmailInput} from './elements/EmailInput';
import {DateInput} from './elements/DateInput';
import {CheckboxInput} from './elements/CheckboxInput';
import {CheckboxGroup} from './elements/CheckboxGroup';
import {RadioInput} from './elements/RadioInput';
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
	private getPageRoot(): Cypress.Chainable {
		return this.get().parent();
	}

	private findByName(selector: string, name: string): Cypress.Chainable {
		return this.get().find(`${selector}[name="${name}"]`).first();
	}

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
			this.get()
				.contains('legend.fmdb-fieldset-legend', legend)
				.closest('fieldset.fmdb-fieldset')
		);
	}

	/**
	 * Get a text input directly in the form (not in a fieldset) by its name attribute
	 */
	getTextInput(name: string): TextInput {
		return new TextInput(
			this.findByName('input[type="text"]', name)
		);
	}

	/**
	 * Get an email input directly in the form by its name attribute
	 */
	getEmailInput(name: string): EmailInput {
		return new EmailInput(
			this.findByName('input[type="email"]', name)
		);
	}

	/**
	 * Get a date input directly in the form by its name attribute
	 */
	getDateInput(name: string): DateInput {
		return new DateInput(
			this.findByName('input[type="date"]', name)
		);
	}

	/**
	 * Get a datetime-local input directly in the form by its name attribute
	 */
	getDateTimeLocalInput(name: string): DateTimeLocalInput {
		return new DateTimeLocalInput(
			this.findByName('input[type="datetime-local"]', name)
		);
	}

	/**
	 * Get a color input directly in the form by its name attribute
	 */
	getColorInput(name: string): ColorInput {
		return new ColorInput(
			this.findByName('input[type="color"]', name)
		);
	}

	/**
	 * Get a single checkbox directly in the form by its name attribute
	 */
	getCheckbox(name: string): CheckboxInput {
		return new CheckboxInput(
			this.findByName('input[type="checkbox"]', name)
		);
	}

	/**
	 * Get a checkbox group directly in the form by its name attribute
	 */
	getCheckboxGroup(name: string): CheckboxGroup {
		return new CheckboxGroup(
			this.get().find(`fieldset.fmdb-checkbox-group:has(input[name="${name}"])`).first()
		);
	}

	/**
	 * Get a radio group directly in the form by its name attribute
	 */
	getRadioGroup(name: string): RadioGroup {
		return new RadioGroup(
			this.get().find(`fieldset.fmdb-radio-group:has(input[name="${name}"])`).first()
		);
	}

	getRadio(name: string): RadioInput {
		return new RadioInput(
			this.findByName('input[type="radio"]', name)
		);
	}

	/**
	 * Get a select input directly in the form by its name attribute
	 */
	getSelectInput(name: string): SelectInput {
		return new SelectInput(
			this.findByName('select', name)
		);
	}

	/**
	 * Get a textarea directly in the form by its name attribute
	 */
	getTextarea(name: string): TextareaInput {
		return new TextareaInput(
			this.findByName('textarea', name)
		);
	}

	/**
	 * Get a file input directly in the form by its name attribute
	 */
	getFileInput(name: string): FileInput {
		return new FileInput(
			this.findByName('input[type="file"]', name)
		);
	}

	/**
	 * Get a button by its name attribute or text content
	 */
	getButton(identifier: string): ButtonInput {
		return new ButtonInput(
			this.get().find(`button:contains("${identifier}"), button[name="${identifier}"]`).first()
		);
	}

	/**
	 * Get a hidden input directly in the form by its name attribute
	 */
	getHiddenInput(name: string): HiddenInput {
		return new HiddenInput(
			this.findByName('input[type="hidden"]', name)
		);
	}

	getRichText(): Cypress.Chainable {
		return this.get().find('.fmdb-content.fmdb-content-text');
	}

	getMessage(): Cypress.Chainable {
		return this.getPageRoot().find('.fmdb-message[role="alert"]');
	}

	getSuccessMessage(): Cypress.Chainable {
		return this.getPageRoot().find('.fmdb-message.fmdb-message-success[role="alert"]');
	}

	getErrorMessage(): Cypress.Chainable {
		return this.getPageRoot().find('.fmdb-message.fmdb-message-error[role="alert"]');
	}

	getNextButton(): ButtonInput {
		return new ButtonInput(
			this.get().find('.fmdb-form-actions .fmdb-next-btn').first()
		);
	}

	getPreviousButton(): ButtonInput {
		return new ButtonInput(
			this.get().find('.fmdb-form-actions .fmdb-prev-btn').first()
		);
	}

	getNewFormButton(): ButtonInput {
		return new ButtonInput(
			this.getPageRoot().find('.fmdb-message .fmdb-new-form-btn').first()
		);
	}

	getStepIndicators(): Cypress.Chainable {
		return this.get().find('.fmdb-steps-nav .fmdb-step-indicator');
	}

	getCurrentStepIndicator(): Cypress.Chainable {
		return this.get().find('.fmdb-steps-nav .fmdb-step-indicator[aria-current="step"]').first();
	}

	getVisibleStep(): Cypress.Chainable {
		return this.get().find('[data-fmdb-step]:visible').first();
	}


	/**
	 * Get the submit button
	 */
	getSubmitButton(): ButtonInput {
		return new ButtonInput(
			this.get().find('.fmdb-form-actions button[type="submit"]').first()
		);
	}

	/**
	 * Get the reset button
	 */
	getResetButton(): ButtonInput {
		return new ButtonInput(
			this.get().find('.fmdb-form-actions button[type="reset"]').first()
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

	nextStep(): void {
		this.getNextButton().click();
	}

	previousStep(): void {
		this.getPreviousButton().click();
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
		this.getSuccessMessage().should('contain', text);
		return this;
	}

	shouldHaveErrorMessage(text: string): this {
		this.getErrorMessage().should('contain', text);
		return this;
	}

	waitForSubmit(): this {
		this.getMessage().should('be.visible');
		return this;
	}

	shouldHaveCurrentStep(label: string): this {
		this.getCurrentStepIndicator().should('contain', label);
		return this;
	}

	shouldHaveVisibleStepCount(count: number): this {
		this.getStepIndicators().should('have.length', count);
		return this;
	}
}
