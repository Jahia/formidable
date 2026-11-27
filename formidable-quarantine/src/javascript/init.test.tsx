// @vitest-environment jsdom
import {describe, expect, it, vi} from 'vitest';

// What the registry receives, and the namespaces the module loads; the screen itself is not rendered here.
const registered = vi.hoisted(() => [] as Array<{type: string; key: string; options: Record<string, unknown>}>);
const namespaces = vi.hoisted(() => [] as string[]);

vi.mock('@jahia/ui-extender', () => ({
    registry: {add: (type: string, key: string, options: Record<string, unknown>) => registered.push({type, key, options})}
}));
vi.mock('i18next', () => ({default: {loadNamespaces: (namespace: string) => namespaces.push(namespace)}}));
vi.mock('./quarantine', () => ({QuarantineApp: () => null}));

describe('the registration of the module', () => {
    it('adds the manager screen to jContent, next to Form Results, on a site that has the module only', async () => {
        const {default: register} = await import('./init');
        register();
        const callback = registered.find(entry => entry.type === 'callback' && entry.key === 'FormidableQuarantine');
        expect(callback?.options.targets).toEqual(['jahiaApp-init:21']);
        (callback?.options.callback as () => void)();

        expect(namespaces).toEqual(['formidable-quarantine']);
        const route = registered.find(entry => entry.type === 'adminRoute' && entry.key === 'formidableQuarantine');
        expect(route?.options).toMatchObject({
            targets: ['jcontent:51'],
            label: 'formidable-quarantine:quarantine.nav.title',
            isSelectable: true,
            requireModuleInstalledOnSite: 'formidable-quarantine'
        });
        expect(typeof route?.options.render).toBe('function');
    });
});
