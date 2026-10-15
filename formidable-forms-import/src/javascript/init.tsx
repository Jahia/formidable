import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import {ImportAction} from './import';

/** The registry target of the Results page toolbar (formidable-engine, FormResults.utils RESULTS_TOOLBAR_TARGET). */
const RESULTS_TOOLBAR_TARGET = 'formidable-results/toolbar';

export default function () {
    // After the engine's own registration (jahiaApp-init:20): the page the action sits in exists first.
    registry.add('callback', 'FormidableFormsImport', {
        targets: ['jahiaApp-init:21'],
        callback: () => {
            i18next.loadNamespaces('formidable-forms-import');

            // The Import button and its dialog, an action of the Results page toolbar (docs/architecture/forms-import.md).
            registry.add('action', 'formidableResultsImport', {
                targets: [`${RESULTS_TOOLBAR_TARGET}:100`],
                component: ImportAction
            });

            console.debug('%c Formidable Forms Import is activated', 'color: #3c8cba');
        }
    });
}
