import {describe, expect, it} from 'vitest';
import {formTitle, refuseFile, type ImportReportForm} from './import.utils';

const fileOf = (name: string, size: number, type = ''): File => {
    const file = new File([new Uint8Array(1)], name, {type});
    Object.defineProperty(file, 'size', {value: size});
    return file;
};

describe('refuseFile', () => {
    it('takes a zip under the bound', () => {
        expect(refuseFile(fileOf('formFactory.zip', 1024), 200)).toBeNull();
        expect(refuseFile(fileOf('EXPORT.ZIP', 1024), 200)).toBeNull();
        expect(refuseFile(fileOf('export', 1024, 'application/zip'), 200)).toBeNull();
    });

    it('refuses anything but a zip, and a zip past the bound', () => {
        expect(refuseFile(fileOf('repository.xml', 1024), 200)).toEqual('notZip');
        expect(refuseFile(fileOf('export.zip', 201 * 1024 * 1024), 200)).toEqual('tooLarge');
    });
});

describe('formTitle', () => {
    const form = {sourceName: 'contact-us', titles: {en: 'Contact Us', fr: 'Contact'}} as unknown as ImportReportForm;

    it('prefers the language of the user, then any language, then the source name', () => {
        expect(formTitle(form, 'fr')).toEqual('Contact');
        expect(formTitle(form, 'de')).toEqual('Contact Us');
        expect(formTitle({sourceName: 'newsletter', titles: {}} as ImportReportForm, 'en')).toEqual('newsletter');
    });
});
