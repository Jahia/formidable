import React from 'react';
import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import {Security} from '@jahia/moonstone';
import {QuarantineApp} from './quarantine';

export default function formidableQuarantine() {
    // After the engine's own registration (jahiaApp-init:20): the route sits next to Form Results.
    registry.add('callback', 'FormidableQuarantine', {
        targets: ['jahiaApp-init:21'],
        callback: () => {
            i18next.loadNamespaces('formidable-quarantine');

            // The manager screen of the quarantine (docs/architecture/quarantine.md, "Manager screen"), on a site that
            // has the module only. A user who does not manage the site gets the "no access" text of the screen.
            registry.add('adminRoute', 'formidableQuarantine', {
                targets: ['jcontent:51'],
                icon: <Security/>,
                label: 'formidable-quarantine:quarantine.nav.title',
                isSelectable: true,
                requireModuleInstalledOnSite: 'formidable-quarantine',
                render: () => <QuarantineApp/>
            });

            console.debug('%c Formidable Quarantine is activated', 'color: #3c8cba');
        }
    });
}
