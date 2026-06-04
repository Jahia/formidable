import {
	getInputTextNode,
	getRichTextNode,
	INPUT_TEXT_SIMPLE,
	RICH_TEXT_CONTENT
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 25 Richtext', () => {
	useFormidableSite();

	it('renders rich text content, link and image in live mode', () => {
		createPublishedLiveFormPage(
			'richtext-form',
			'Richtext Form',
			[
				getRichTextNode(RICH_TEXT_CONTENT),
				getInputTextNode(INPUT_TEXT_SIMPLE)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.getRichText().should('contain.html', '<strong>Welcome</strong>');
			form.getRichText().find('a')
				.should('have.attr', 'href', 'https://example.com/docs')
				.and('contain', 'Read the documentation');
			form.getRichText().find('img')
				.should('have.attr', 'alt', 'Placeholder illustration')
				.and('have.attr', 'src')
				.and('contain', 'via.placeholder.com/120x80');
		});
	});
});
