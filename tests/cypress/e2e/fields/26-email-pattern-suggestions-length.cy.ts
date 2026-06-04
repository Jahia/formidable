import {
	getInputEmailNode,
	INPUT_EMAIL_WITH_LIST
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 26 Email pattern suggestions and length', () => {
	useFormidableSite();

	it('applies pattern, suggestions, min and max length validation as configured', () => {
		createPublishedLiveFormPage(
			'email-validation-form',
			'Email Validation Form',
			[getInputEmailNode({
				...INPUT_EMAIL_WITH_LIST,
				required: true,
				pattern: '[a-z0-9._%+-]+@example\\.com',
				minLength: 16,
				maxLength: 25,
				placeholder: 'name@example.com'
			})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const emailInput = form.getEmailInput(INPUT_EMAIL_WITH_LIST.name!);

			emailInput
				.shouldBeVisible()
				.shouldBeRequired()
				.shouldHavePlaceholder('name@example.com')
				.shouldHavePattern('[a-z0-9._%+-]+@example\\.com')
				.shouldHaveMinLength(16)
				.shouldHaveMaxLength(25)
				.shouldHaveDatalist()
				.shouldHaveDatalistOptions(INPUT_EMAIL_WITH_LIST.list!);

			emailInput.type('bad');
			emailInput.shouldBeInvalid();

			emailInput.clear().type('user@gmail.com');
			emailInput.shouldBeInvalid();

			emailInput.clear().type('qa@example.com');
			emailInput.shouldBeInvalid();

			emailInput.clear().type('product.team@example.com');
			emailInput.shouldBeValid();
		});
	});
});
