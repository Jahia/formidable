import {BaseComponent, getComponentBySelector} from '@jahia/cypress';

class ConditionalLogicRuleRow extends BaseComponent {
	static defaultSelector = 'div.flexRow_nowrap.flexFluid.alignCenter';
}

export class ConditionalLogicField extends BaseComponent {
	static defaultSelector = '[data-sel-content-editor-field="fmdbmix:formLogicElement_logics"]';

	private static readonly menuSelector = '.moonstone-menu:not(.moonstone-hidden)';
	private static readonly menuOverlaySelector = '.moonstone-menu_overlay';

	private getAddRuleButton(): Cypress.Chainable<JQuery<HTMLElement>> {
		return this.get().find('button[data-sel-action="addField"]', {timeout: 15000});
	}

	private dismissOpenMenu(): void {
		cy.get('body').then($body => {
			const $overlay = $body.find(ConditionalLogicField.menuOverlaySelector);
			if ($overlay.length > 0) {
				cy.wrap($overlay.first()).click('topLeft', {force: true});
				return;
			}

			if ($body.find(ConditionalLogicField.menuSelector).length > 0) {
				cy.get('body').type('{esc}', {force: true});
			}
		});

		cy.get('body', {timeout: 15000}).should($body => {
			expect($body.find(ConditionalLogicField.menuSelector).length).to.equal(0);
		});
	}

	private getDropdown(ruleIndex: number, dropdownIndex: number): Cypress.Chainable<JQuery<HTMLElement>> {
		return this.getRule(ruleIndex).get()
			.find('.moonstone-dropdown_container')
			.eq(dropdownIndex);
	}

	private getOpenMenu(ruleIndex: number, dropdownIndex: number): Cypress.Chainable<JQuery<HTMLElement>> {
		return this.getDropdown(ruleIndex, dropdownIndex)
			.find(ConditionalLogicField.menuSelector, {timeout: 15000});
	}

	waitUntilReady(): this {
		this.get().should('be.visible');
		this.get().find('.moonstone-loader').should('have.length', 0);
		this.getAddRuleButton().should('be.visible');
		return this;
	}

	addRule(): this {
		this.waitUntilReady();
		this.getAddRuleButton().click();
		this.get().find(ConditionalLogicRuleRow.defaultSelector, {timeout: 15000}).should($rules => {
			expect($rules.length).to.be.at.least(1);
		});
		this.getRule(0).get().find('.moonstone-loader').should('have.length', 0);
		this.getRule(0).get().find('.moonstone-dropdown_container', {timeout: 15000}).should('have.length.at.least', 2);
		return this;
	}

	getRule(index: number): ConditionalLogicRuleRow {
		return getComponentBySelector(
			ConditionalLogicRuleRow,
			`${ConditionalLogicRuleRow.defaultSelector}:eq(${index})`,
			this
		);
	}

	openDropdown(ruleIndex: number, dropdownIndex: number): this {
		this.dismissOpenMenu();
		this.getRule(ruleIndex).get().find('.moonstone-loader').should('have.length', 0);
		this.getDropdown(ruleIndex, dropdownIndex).scrollIntoView().click();
		// eslint-disable-next-line cypress/no-unnecessary-waiting
		cy.wait(500);
		this.getOpenMenu(ruleIndex, dropdownIndex).should('be.visible');
		return this;
	}

	selectMenuItem(label: string): this {
		cy.get(ConditionalLogicField.menuSelector).contains('.moonstone-menuItem', label).trigger('click');
		return this;
	}

	menuShouldHaveItems(labels: string[]): this {
		for (const label of labels) {
			cy.get(ConditionalLogicField.menuSelector).contains('.moonstone-menuItem', label).scrollIntoView().should('be.visible');
		}

		return this;
	}

	menuShouldNotHaveItems(labels: string[]): this {
		cy.get(ConditionalLogicField.menuSelector).find('.moonstone-menuItem').then($items => {
			const itemLabels = Array.from($items, item => item.textContent?.trim() ?? '');
			for (const label of labels) {
				expect(itemLabels).not.to.include(label);
			}
		});

		return this;
	}

	closeMenu(): this {
		this.dismissOpenMenu();
		return this;
	}

	selectSource(ruleIndex: number, label: string): this {
		this.openDropdown(ruleIndex, 0);
		this.selectMenuItem(label);
		return this;
	}

	selectOperator(ruleIndex: number, label: string): this {
		this.openDropdown(ruleIndex, 1);
		this.selectMenuItem(label);
		return this;
	}

	selectValue(ruleIndex: number, label: string): this {
		this.openDropdown(ruleIndex, 2);
		this.selectMenuItem(label);
		this.dismissOpenMenu();
		return this;
	}

	ruleShouldHaveDropdownCount(ruleIndex: number, count: number): this {
		this.getRule(ruleIndex).get().find('.moonstone-dropdown_container').should('have.length', count);
		return this;
	}

	ruleShouldHaveDateInputCount(ruleIndex: number, count: number): this {
		this.getRule(ruleIndex).get().find('input[type="date"]').should('have.length', count);
		return this;
	}

	ruleShouldContainText(ruleIndex: number, text: string): this {
		this.getRule(ruleIndex).should('contain.text', text);
		return this;
	}
}
