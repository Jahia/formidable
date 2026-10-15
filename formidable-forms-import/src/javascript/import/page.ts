/**
 * What the import takes from the Results page it sits in, as the page's own app defines it: the UI context
 * of jContent, the size of a file as the page prints it, and a download through the browser. Copied, not
 * shared: the two apps are federated bundles of their own.
 */
interface UiContext {
    siteKey?: string;
    uilang?: string;
    /** The servlet context path, empty at the root, `/dx` on an instance deployed under /dx. */
    contextPath?: string;
}

/** Typed access to Jahia's global UI context (window.contextJsParameters). */
export function uiContext(): UiContext {
    return (globalThis as typeof globalThis & {contextJsParameters?: UiContext}).contextJsParameters ?? {};
}

export function formatFileSize(bytes: number | null): string {
    if (bytes == null) {
        return '';
    }

    if (bytes < 1024) {
        return `${bytes} B`;
    }

    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }

    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export const sanitizeFilenamePart = (value: string): string => value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'form-results';

export const downloadFile = (filename: string, content: string, mimeType: string) => {
    const blob = new Blob([content], {type: mimeType});
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
};
