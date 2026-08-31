import {registry} from '@jahia/ui-extender';

/**
 * Page Builder box look per form level. A multi-step form is authored flat, so the grouping
 * levels stack on one page (field list > step or fieldset > field): steps and fieldsets — the
 * two ways of grouping fields — share one gold, so a contributor reads "a group" at a glance
 * whatever its type. The field list keeps jContent's default box on purpose: it is the neutral
 * frame around everything, not a level to spot. Purple is left to jExperience, orange to
 * jContent's warnings.
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
        borderColor: '#a8945f',
        backgroundColors: {base: '#f7f4ec', hover: '#ede7d6', selected: '#dcd2b4'}
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
