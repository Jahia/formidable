import {getInputTextNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 211 Text input mask', () => {
	useFormidableSite();

	it('formats typed input according to the mask and keeps native validation working', () => {
		createPublishedLiveFormPage(
			'text-mask-form',
			'Text Mask Form',
			[
				getInputTextNode({
					name: 'employeeCode',
					title: 'Employee code',
					mask: 'AA-9999',
					placeholder: 'AB-1234'
				}),
				getInputTextNode({
					name: 'extension',
					title: 'Phone extension',
					mask: '(99)'
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const input = form.getTextInput('employeeCode');

			// The mask is exposed on the input and derives the HTML pattern for no-JS validation.
			input
				.shouldBeVisible()
				.shouldHaveMask('AA-9999')
				.shouldHavePattern('^[A-Za-z][A-Za-z]-[0-9][0-9][0-9][0-9]$');

			// Letters are uppercased and the literal dash is inserted automatically.
			input.type('ab1234');
			input.shouldHaveValue('AB-1234');

			// Characters rejected by the current mask token are dropped, not truncating the rest.
			input.clear().type('ab12cd34');
			input.shouldHaveValue('AB-1234');

			// Input beyond the mask length is ignored.
			input.clear().type('ab12345678');
			input.shouldHaveValue('AB-1234');

			// Trailing fixed literals are completed automatically so the derived pattern can be satisfied.
			const extension = form.getTextInput('extension');
			extension
				.shouldHaveMask('(99)')
				.shouldHavePattern('^\\([0-9][0-9]\\)$');
			extension.type('12');
			extension.shouldHaveValue('(12)');
			extension.shouldBeValid();

			// The masked value satisfies the derived pattern and the form submits.
			input.shouldBeValid();
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});

	it('pre-formats the default value server-side', () => {
		createPublishedLiveFormPage(
			'text-mask-default-form',
			'Text Mask Default Form',
			[getInputTextNode({
				name: 'phoneNumber',
				title: 'Phone number',
				mask: '(99) 9999-9999',
				defaultValue: '1122333344'
			})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const input = form.getTextInput('phoneNumber');

			// The default value is rendered already masked, without any client-side reformat flicker.
			input
				.shouldBeVisible()
				.shouldHaveMask('(99) 9999-9999')
				.shouldHaveValue('(11) 2233-3344');
		});
	});
});
