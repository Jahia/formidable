import {BaseComponent} from '@jahia/cypress';

export class FormElement extends BaseComponent {
	getContainer(): Cypress.Chainable {
		return this.get().closest('.fmdb-form-group');
	}

	getValidationError(): Cypress.Chainable {
		return this.getContainer().find('.fmdb-validation-error');
	}

	getLabel(): Cypress.Chainable {
		return this.getContainer().find('label, legend');
	}

	getInput(): Cypress.Chainable {
		return this.get();
	}

	isRequired(): Cypress.Chainable<boolean> {
		return this.getInput().then($el => $el.prop('required') as boolean);
	}

	shouldBeVisible(): this {
		this.get().should('be.visible');
		return this;
	}

	shouldHaveLabel(text: string): this {
		this.getLabel().should('contain', text);
		return this;
	}

	shouldBeRequired(): this {
		this.isRequired().should('be.true');
		this.getLabel().find('.fmdb-required-indicator').should('exist');
		return this;
	}

	shouldNotBeRequired(): this {
		this.getInput().should('not.have.attr', 'required');
		this.getLabel().find('.fmdb-required-indicator').should('not.exist');
		return this;
	}

	shouldBeValid(): this {
		this.getInput().then($el => {
			expect(($el.get(0) as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).checkValidity()).to.eq(true);
		});
		return this;
	}

	shouldBeInvalid(): this {
		this.getInput().then($el => {
			expect(($el.get(0) as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).checkValidity()).to.eq(false);
		});
		return this;
	}

	shouldHaveValidationError(text: string): this {
		this.getValidationError().should('contain', text);
		return this;
	}

	shouldNotHaveValidationError(): this {
		this.getValidationError().should('not.exist');
		return this;
	}

	shouldBeMarkedInvalid(): this {
		this.getInput().should('have.class', 'fmdb-invalid');
		return this;
	}

	shouldNotBeMarkedInvalid(): this {
		this.getInput().should('not.have.class', 'fmdb-invalid');
		return this;
	}

	shouldBeFocused(): this {
		this.getInput().should('be.focused');
		return this;
	}

	shouldHaveValidityState(state: keyof ValidityState, expected: boolean): this {
		this.getInput().then($el => {
			const input = $el.get(0) as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
			expect(input.validity[state]).to.eq(expected);
		});
		return this;
	}
}
