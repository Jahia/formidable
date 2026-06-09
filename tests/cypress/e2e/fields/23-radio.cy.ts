import {
	getRadioNode,
	RADIO_GROUP,
	RADIO_SINGLE
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 23 Radio', () => {
	useFormidableSite();

	it('renders a required standalone radio option', () => {
		createPublishedLiveFormPage(
			'radio-single-form',
			'Radio Single Form',
			[getRadioNode(RADIO_SINGLE)]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.getRadio(RADIO_SINGLE.name!)
				.shouldBeVisible()
				.shouldBeRequired()
				.shouldHaveValue(RADIO_SINGLE.choices[0].value)
				.shouldBeSelected();
		});
	});

	it('renders a required radio group and switches selection correctly', () => {
		createPublishedLiveFormPage(
			'radio-group-form',
			'Radio Group Form',
			[getRadioNode(RADIO_GROUP)]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const group = form.getRadioGroup(RADIO_GROUP.name!);

			group
				.shouldBeVisible()
				.shouldHaveLegend(RADIO_GROUP.title!)
				.shouldBeRequired()
				.shouldHaveSelected('Express')
				.select('Pickup')
				.shouldHaveSelected('Pickup');
		});
	});
});
