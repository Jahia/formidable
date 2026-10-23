import {publishAndWaitJobEnding, setNodeProperty} from '@jahia/cypress';
import {createPublishedLiveFormPage, getInputTextNode, getStepNode, visitLiveForm} from '../../support/fixtures';
import type {JahiaNode} from '../../support/fixtures';
import {Form} from '../../page-object';
import {useFormidableSite} from './support';

/**
 * The Form experience setting (formExperience on fmdbmix:multiStep) switched on a published form, both ways,
 * for a form with steps and a form without: Classic shows the whole form or one step at a time,
 * One-at-a-time one field per screen. A form without the property, as created before it existed, is Classic.
 */
const fields = (suffix: string) => ['first', 'second', 'third'].map(name =>
	getInputTextNode({name: `${name}${suffix}`, title: `${name} field`}));

const LAYOUTS: Array<{label: string; suffix: string; build: (nodes: JahiaNode[]) => JahiaNode[]}> = [
	{label: 'without steps', suffix: 'Flat', build: nodes => nodes},
	{
		label: 'with steps',
		suffix: 'Steps',
		build: ([first, second, third]) => [
			getStepNode({name: 'stepOne', title: 'Step one', label: 'Step one', children: [first, second]}),
			getStepNode({name: 'stepTwo', title: 'Step two', label: 'Step two', children: [third]})
		]
	}
];

const setExperience = (formPath: string, value: string) => {
	setNodeProperty(formPath, 'formExperience', value, 'en');
	publishAndWaitJobEnding(formPath, ['en']);
};

describe('Validation - 47 One-at-a-time switched on and off', () => {
	useFormidableSite();

	LAYOUTS.forEach(({label, suffix, build}) => {
		const [first, second, third] = ['first', 'second', 'third'].map(name => `${name}${suffix}`);

		const shouldBeClassic = (form: Form) => {
			form.waitUntilHydrated();
			form.get().should('not.have.attr', 'data-fmdb-experience');
			form.get().find('[data-fmdb-screen]').should('not.exist');
			// The first two fields together: the whole form, or the whole of the first step
			form.getTextInput(first).get().should('be.visible');
			form.getTextInput(second).get().should('be.visible');
			if (suffix === 'Flat') {
				form.getTextInput(third).get().should('be.visible');
				form.getSubmitButton().get().should('be.visible');
			} else {
				form.getTextInput(third).get().should('not.be.visible');
				form.shouldHaveCurrentStep('Step one');
			}
		};

		const shouldBeOneAtATime = (form: Form) => {
			form.waitUntilHydrated();
			form.get().should('have.attr', 'data-fmdb-experience', 'oneAtATime');
			form.shouldShowScreenOf(first);
			form.getTextInput(second).get().should('not.be.visible');
			form.getTextInput(third).get().should('not.be.visible');
			form.nextStep();
			form.shouldShowScreenOf(second);
			form.getTextInput(first).get().should('not.be.visible');
		};

		it(`switches a form ${label} from Classic to One-at-a-time and back`, () => {
			createPublishedLiveFormPage(`switch-form-${suffix.toLowerCase()}`, `Switch Form ${suffix}`, build(fields(suffix)))
				.then(({livePath, formPath}) => {
					// No property: the forms created before the setting are Classic
					shouldBeClassic(visitLiveForm(livePath));

					setExperience(formPath, 'oneAtATime');
					shouldBeOneAtATime(visitLiveForm(livePath));

					setExperience(formPath, 'classic');
					shouldBeClassic(visitLiveForm(livePath));
				});
		});
	});
});
