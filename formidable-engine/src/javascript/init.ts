import {registry} from '@jahia/ui-extender';
import {SelectOptionsCmp} from './SelectOptions/SelectOptionsCmp';

export default function () {
    registry.add('callback', 'FormidableEngineEditor', {
        targets: ['jahiaApp-init:20'],
        callback: () => {
            registry.add('selectorType', 'SelectOptions', {cmp: SelectOptionsCmp, supportMultiple: false});
            console.debug('%c Formidable Engine Editor Extensions is activated', 'color: #3c8cba');
        }
    });
}
