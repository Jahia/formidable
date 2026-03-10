import {BaseComponent} from '@jahia/cypress';
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
 * Fieldset component - Groups form elements together
 * Corresponds to fmdb:fieldset node type
 */
export class Fieldset extends BaseComponent {
	/**
	 * Get the legend element of this fieldset
	 */
	getLegend(): Cypress.Chainable {
		return this.get().find('legend.fmdb-fieldset-legend');
	}

	/**
	 * Get the container with all fieldset elements
	 */
	getElements(): Cypress.Chainable {
		return this.get().find('.fmdb-fieldset-elements');
	}

	/**
	 * Get a text input by its name attribute
	 */
	getTextInput(name: string): TextInput {
		return new TextInput(
			this.getElements().find(`input[type="text"][name="${name}"]`)
		);
	}

	/**
	 * Get an email input by its name attribute
	 */
	getEmailInput(name: string): EmailInput {
		return new EmailInput(
			this.getElements().find(`input[type="email"][name="${name}"]`)
		);
	}

	/**
	 * Get a date input by its name attribute
	 */
	getDateInput(name: string): DateInput {
		return new DateInput(
			this.getElements().find(`input[type="date"][name="${name}"]`)
		);
	}

	/**
	 * Get a datetime-local input by its name attribute
	 */
	getDateTimeLocalInput(name: string): DateTimeLocalInput {
		return new DateTimeLocalInput(
			this.getElements().find(`input[type="datetime-local"][name="${name}"]`)
		);
	}

	/**
	 * Get a color input by its name attribute
	 */
	getColorInput(name: string): ColorInput {
		return new ColorInput(
			this.getElements().find(`input[type="color"][name="${name}"]`)
		);
	}

	/**
	 * Get a single checkbox by its name attribute
	 */
	getCheckbox(name: string): CheckboxInput {
		return new CheckboxInput(
			this.getElements().find(`input[type="checkbox"][name="${name}"]`)
		);
	}

	/**
	 * Get a checkbox group by its name attribute
	 * The name is typically the shared name of all checkboxes in the group
	 */
	getCheckboxGroup(name: string): CheckboxGroup {
		return new CheckboxGroup(
			this.getElements().find(`fieldset.fmdb-checkbox-group:has(input[name="${name}"])`).first()
		);
	}

	/**
	 * Get a radio group by its name attribute
	 */
	getRadioGroup(name: string): RadioGroup {
		return new RadioGroup(
			this.getElements().find(`fieldset.fmdb-radio-group:has(input[name="${name}"])`).first()
		);
	}

	/**
	 * Get a select input by its name attribute
	 */
	getSelectInput(name: string): SelectInput {
		return new SelectInput(
			this.getElements().find(`select[name="${name}"]`)
		);
	}

	/**
	 * Get a textarea by its name attribute
	 */
	getTextarea(name: string): TextareaInput {
		return new TextareaInput(
			this.getElements().find(`textarea[name="${name}"]`)
		);
	}

	/**
	 * Get a file input by its name attribute
	 */
	getFileInput(name: string): FileInput {
		return new FileInput(
			this.getElements().find(`input[type="file"][name="${name}"]`)
		);
	}

	/**
	 * Get a button input by its text content or name attribute
	 */
	getButtonInput(identifier: string): ButtonInput {
		return new ButtonInput(
			this.getElements().find(`button:contains("${identifier}"), button[name="${identifier}"]`).first()
		);
	}

	/**
	 * Get a hidden input by its name attribute
	 */
	getHiddenInput(name: string): HiddenInput {
		return new HiddenInput(
			this.getElements().find(`input[type="hidden"][name="${name}"]`)
		);
	}

	shouldBeVisible(): this {
		this.get().should('be.visible');
		return this;
	}

	/**
	 * Assert that the legend contains the expected text
	 */
	shouldHaveLegend(text: string): this {
		this.getLegend().should('contain', text);
		return this;
	}
}
