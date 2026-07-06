import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import {SelectOptionsCmp} from './SelectOptions/SelectOptionsCmp';
import {ConditionalLogicCmp} from './ConditionalLogic/ConditionalLogicCmp';
import {FormResultsApp} from './FormResults';
import {Form} from '@jahia/moonstone';

export default function () {
    registry.add('callback', 'FormidableEngineEditor', {
        targets: ['jahiaApp-init:20'],
        callback: () => {
            i18next.loadNamespaces('formidable-engine');

            registry.add('selectorType', 'SelectOptions', {cmp: SelectOptionsCmp, supportMultiple: false});
            registry.add('selectorType', 'ConditionalLogic', {cmp: ConditionalLogicCmp, supportMultiple: false});

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

    // The helpText fields carry the CKEditor 4 config URL in their definition
    // (richtext[ckeditor.customConfig='...helpTextConfig.js']). When the
    // richtext-ckeditor5 module is installed, Content Editor resolves the same
    // selector option value as a 'ckeditor5-config' registry key instead, so
    // the equivalent CKEditor 5 config is registered under that exact string.
    // Registered after the richtext-ckeditor5 defaults (jahiaApp-init:99) so
    // the 'minimal' preset is available to extend.
    registry.add('callback', 'FormidableCKEditor5Config', {
        targets: ['jahiaApp-init:99.5'],
        callback: () => {
            // The 'light' preset without undo/redo, heading, removeFormat,
            // bookmark, insertTable and indentation, plus underline.
            const lightConfig = registry.get('ckeditor5-config', 'light');
            registry.add('ckeditor5-config', '$context/modules/formidable-engine/javascript/ckeditor/helpTextConfig.js', {
                ...lightConfig,
                toolbar: {
                    items: ['bold', 'italic', 'underline', '|', 'insertJahiaImage', 'link', '|', 'bulletedList', 'numberedList'],
                    shouldNotGroupWhenFull: true
                },
                menuBar: {isVisible: false}
            });
        }
    });
}
