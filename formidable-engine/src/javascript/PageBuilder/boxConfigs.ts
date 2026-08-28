import {registry} from '@jahia/ui-extender';

/**
 * Page Builder box look per form level. A multi-step form is authored flat, so three levels of
 * boxes stack on one page (field list > step or fieldset > field): the hover outline and the bar
 * of each level take the colour of its authoring zone (see authoring.css in the elements module),
 * which is also the colour of its mixin icon on the create buttons — one grey base declined in gold for steps, blue for fieldsets (a fieldset is a
 * form field), Moonstone light grey for the field list. Purple is left to jExperience, orange to jContent's warnings.
 *
 * jContent looks the config up with registry.find({type: 'pageBuilderBoxConfig', target: nodeType});
 * targets are passed as objects because a string target is split on ':' (namespace lost).
 */
const boxConfigs: Record<string, {borderColor: string; backgroundColors: {base: string; hover: string; selected: string}}> = {
    'fmdb:step': {
        borderColor: '#a8945f',
        backgroundColors: {base: '#f7f4ec', hover: '#ede7d6', selected: '#dcd2b4'}
    },
    'fmdb:fieldset': {
        borderColor: '#607ba8',
        backgroundColors: {base: '#eef2f8', hover: '#dde5f0', selected: '#c5d2e6'}
    },
    'fmdb:fieldList': {
        // The list is the neutral level: Moonstone's own light greys (resolved in the
        // Page Builder iframe, which loads the Moonstone tokens), so it recedes behind
        // the coloured steps and fields instead of weighing on the page.
        borderColor: 'var(--moon-color-gray_light40)',
        backgroundColors: {base: 'var(--moon-color-gray_light_plain40)', hover: '#e5e5e5', selected: 'var(--moon-color-gray_light)'}
    }
};

export const registerPageBuilderBoxConfigs = () => {
    Object.entries(boxConfigs).forEach(([nodeType, config]) => {
        registry.add('pageBuilderBoxConfig', `formidable-${nodeType.replace(':', '-')}`, {
            targets: [{id: nodeType, priority: 0}],
            ...config
        });
    });
};
