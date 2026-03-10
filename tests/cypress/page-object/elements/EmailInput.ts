import {TextInput} from './TextInput';

export class EmailInput extends TextInput {
	shouldBeValidEmail(): this {
		this.getInput().should('have.attr', 'type', 'email');
		return this;
	}

	typeEmail(email: string): this {
		this.type(email);
		this.getInput().invoke('val').should('contain', '@');
		return this;
	}

	shouldHavePattern(pattern: string): this {
		this.getInput().should('have.attr', 'pattern', pattern);
		return this;
	}

	shouldHaveAutocomplete(value: string): this {
		this.getInput().should('have.attr', 'autocomplete', value);
		return this;
	}


	shouldBeMultiple(): this {
		this.getInput().should('have.attr', 'multiple');
		return this;
	}

	shouldNotBeMultiple(): this {
		this.getInput().should('not.have.attr', 'multiple');
		return this;
	}

	shouldHaveList(listId: string): this {
		this.getInput().should('have.attr', 'list', listId);
		return this;
	}

	shouldHaveDatalist(): this {
		this.getInput().should('have.attr', 'list');
		return this;
	}

	shouldNotHaveDatalist(): this {
		this.getInput().should('not.have.attr', 'list');
		return this;
	}

	shouldHaveDatalistOptions(options: string[]): this {
		// Get the list attribute value (datalist ID)
		this.getInput().invoke('attr', 'list').then((listId: string) => {
			if (listId) {
				// Find the datalist within the same parent container as the input
				this.getInput().parent().find(`datalist#${listId}`).should('exist');
				// Check that each option exists in the datalist
				options.forEach((option) => {
					this.getInput().parent().find(`datalist#${listId} option[value="${option}"]`).should('exist');
				});
			}
		});
		return this;
	}
}
