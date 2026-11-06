import {registry} from '@jahia/ui-extender';
import {LOGIC_SOURCE_REGISTRY_TYPE} from '@jahia/formidable-library/logic-sources';
import i18next from 'i18next';
import {PROFILE_SOURCE_ID, profileSource} from './profileSource';

export default function formidableJExperienceEngine() {
    // After the engine's own registration (jahiaApp-init:20); the rule editor reads the registry when it renders.
    registry.add('callback', 'FormidableJExperienceEngine', {
        targets: ['jahiaApp-init:21'],
        callback: () => {
            i18next.loadNamespaces('formidable-jexperience-engine');

            // A visitor's profile property as a conditional-logic source (docs/architecture/jexperience-integration.md).
            registry.add(LOGIC_SOURCE_REGISTRY_TYPE, PROFILE_SOURCE_ID, profileSource);

            console.debug('%c Formidable jExperience Engine is activated', 'color: #3c8cba');
        }
    });
}
