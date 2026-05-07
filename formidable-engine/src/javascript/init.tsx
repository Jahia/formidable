import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import {SelectOptionsCmp} from './SelectOptions/SelectOptionsCmp';
import {FormResultsApp} from './FormResults';
import {Form} from '@jahia/moonstone';

export default function () {
    registry.add('callback', 'FormidableEngineEditor', {
        targets: ['jahiaApp-init:20'],
        callback: () => {
            i18next.loadNamespaces('formidable-engine');

            registry.add('selectorType', 'SelectOptions', {cmp: SelectOptionsCmp, supportMultiple: false});

            registry.add('adminRoute', 'formidableResults', {
                targets: ['jcontent:50'],
                icon: <Form/>,
                label: 'formidable-engine:formResults.nav.title',
                isSelectable: true,
                requireModuleInstalledOnSite: 'formidable-engine',
                render: () => <FormResultsApp/>
            });

            console.debug('%c Formidable Engine Extensions is activated', 'color: #3c8cba');
        }
    });
}
