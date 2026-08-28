import {registry} from '@jahia/ui-extender';

/**
 * Page Builder box look per form level. A multi-step form is authored flat, so three levels of
 * boxes stack on one page (field list > step or fieldset > field): the hover outline and the bar
 * of each level take the colour of its authoring zone (see authoring.css in the elements module),
 * which is also the colour of its mixin icon on the create buttons — one grey base declined in gold for steps, blue for fieldsets (a fieldset is a
 * form field), grey for the field list. Purple is left to jExperience, orange to jContent's warnings.
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
        borderColor: '#8f959e',
        backgroundColors: {base: '#f3f4f6', hover: '#e5e7eb', selected: '#d1d5db'}
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
