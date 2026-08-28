import {registry} from '@jahia/ui-extender';

/**
 * Page Builder box look per form level. A multi-step form is authored flat, so three levels of
 * boxes stack on one page (field list > step or fieldset > field): the hover outline and the bar
 * of each level take the colour of its authoring zone (see authoring.css in the elements module),
 * which is also the colour of its mixin icon on the create buttons — muted gold for steps (kept away from jContent's warning orange), blue for
 * fieldsets, grey for the field list. Purple is left to jExperience.
 *
 * jContent looks the config up with registry.find({type: 'pageBuilderBoxConfig', target: nodeType});
 * targets are passed as objects because a string target is split on ':' (namespace lost).
 */
const boxConfigs: Record<string, {borderColor: string; backgroundColors: {base: string; hover: string; selected: string}}> = {
    'fmdb:step': {
        borderColor: '#a89660',
        backgroundColors: {base: '#f5f2e8', hover: '#ebe6d3', selected: '#dcd3b0'}
    },
    'fmdb:fieldset': {
        borderColor: '#2563eb',
        backgroundColors: {base: '#eff4fd', hover: '#dbe7fb', selected: '#bfd3f7'}
    },
    'fmdb:fieldList': {
        borderColor: '#6b7280',
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
