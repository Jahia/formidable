import {BaseComponent} from '@jahia/cypress';

export class FormElement extends BaseComponent {
	getLabel(): Cypress.Chainable {
		return this.get().parent('.fmdb-form-group').find('label, legend');
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
}

