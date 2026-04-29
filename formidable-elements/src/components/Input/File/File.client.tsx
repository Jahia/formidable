import {type ChangeEvent, useRef, useState} from 'react';
import {formatFileSize} from '~/utils/fileUtils';
import {useTranslation} from "react-i18next";

interface FileInputProps {
	inputId: string;
	inputName: string;
	accept?: string[];
	multiple?: boolean;
	required?: boolean;
}

const normalizeAccept = (accept?: string[]): string[] =>
	(accept ?? []).map(token => token.trim()).filter(Boolean);

const MIME_EXTENSION_MAP: Record<string, string[]> = {
	"application/msword": [".doc"],
	"application/pdf": [".pdf"],
	"application/vnd.ms-excel": [".xls"],
	"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": [".xlsx"],
	"application/vnd.openxmlformats-officedocument.wordprocessingml.document": [".docx"],
	"image/gif": [".gif"],
	"image/jpeg": [".jpg", ".jpeg"],
	"image/png": [".png"],
	"image/webp": [".webp"],
	"text/csv": [".csv"],
};

const getKnownExtensionsForMime = (mimeType: string): string[] =>
	MIME_EXTENSION_MAP[mimeType] ?? [];

const getKnownExtensionsForWildcard = (wildcardMimeType: string): string[] => {
	const prefix = wildcardMimeType.slice(0, -1);
	return Array.from(
		new Set(
			Object.entries(MIME_EXTENSION_MAP)
				.filter(([mimeType]) => mimeType.startsWith(prefix))
				.flatMap(([, extensions]) => extensions)
		)
	);
};

const getDisplayFormats = (acceptTokens: string[]): string[] =>
	Array.from(new Set(acceptTokens.flatMap(token => {
		const loweredToken = token.toLowerCase();
		if (loweredToken.startsWith(".")) {
			return [loweredToken];
		}

		if (loweredToken.endsWith("/*")) {
			const wildcardExtensions = getKnownExtensionsForWildcard(loweredToken);
			return wildcardExtensions.length > 0 ? wildcardExtensions : [token];
		}

		const mimeExtensions = getKnownExtensionsForMime(loweredToken);
		return mimeExtensions.length > 0 ? mimeExtensions : [token];
	})));

const extensionFromName = (fileName: string): string => {
	const dotIndex = fileName.lastIndexOf(".");
	return dotIndex >= 0 ? fileName.slice(dotIndex).toLowerCase() : fileName;
};

const matchesAcceptToken = (file: File, token: string): boolean => {
	const loweredToken = token.toLowerCase();
	const loweredName = file.name.toLowerCase();
	const loweredType = file.type.toLowerCase();

	if (loweredToken.startsWith(".")) {
		return loweredName.endsWith(loweredToken);
	}

	if (loweredToken.endsWith("/*")) {
		const prefix = loweredToken.slice(0, -1);
		if (loweredType) {
			return loweredType.startsWith(prefix);
		}

		const wildcardExtensions = getKnownExtensionsForWildcard(loweredToken);
		return wildcardExtensions.length === 0 || wildcardExtensions.some(extension => loweredName.endsWith(extension));
	}

	if (loweredType) {
		return loweredType === loweredToken;
	}

	const knownExtensions = getKnownExtensionsForMime(loweredToken);
	return knownExtensions.length === 0 || knownExtensions.some(extension => loweredName.endsWith(extension));
};

export default function FileInput(
	{
		inputId,
		inputName,
		accept,
		multiple,
		required
	}: FileInputProps
) {
	const [selectedFiles, setSelectedFiles] = useState<FileList | null>(null);
	const [selectionNotice, setSelectionNotice] = useState<string | null>(null);
	const fileInputRef = useRef<HTMLInputElement>(null);
	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_inputFile'});
	const acceptTokens = normalizeAccept(accept);
	const allowedTypesLabel = getDisplayFormats(acceptTokens)
		.map(format => `"${format}"`)
		.join(", ");

	const syncInputFiles = (files: File[]) => {
		if (!fileInputRef.current) return;

		const dt = new DataTransfer();
		files.forEach(file => dt.items.add(file));
		fileInputRef.current.files = dt.files;
		setSelectedFiles(dt.files.length > 0 ? dt.files : null);
	};

	const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
		const input = event.currentTarget;
		const files = Array.from(input.files ?? []);

		if (files.length === 0) {
			input.setCustomValidity("");
			setSelectionNotice(null);
			setSelectedFiles(null);
			return;
		}

		if (acceptTokens.length === 0) {
			input.setCustomValidity("");
			setSelectionNotice(null);
			setSelectedFiles(input.files && input.files.length > 0 ? input.files : null);
			return;
		}

		const validFiles = files.filter(file => acceptTokens.some(token => matchesAcceptToken(file, token)));
		const invalidFiles = files.filter(file => !validFiles.includes(file));

		if (invalidFiles.length === 0) {
			input.setCustomValidity("");
			setSelectionNotice(null);
			setSelectedFiles(input.files && input.files.length > 0 ? input.files : null);
			return;
		}

		const invalidFormats = Array.from(new Set(invalidFiles.map(file => extensionFromName(file.name))))
			.map(format => `"${format}"`)
			.join(", ");
		const blockingMessage = t(invalidFiles.length > 1 ? "multipleInvalidFiles" : "singleInvalidFile", {
			invalidFormats,
			allowedTypes: allowedTypesLabel,
			interpolation: {escapeValue: false},
		});
		if (validFiles.length === 0) {
			syncInputFiles([]);
			setSelectionNotice(null);
			input.setCustomValidity(blockingMessage);
			input.reportValidity();
			return;
		}

		syncInputFiles(validFiles);
		setSelectionNotice(t(invalidFiles.length > 1 ? "ignoredMultipleInvalidFiles" : "ignoredSingleInvalidFile", {
			invalidFormats,
			allowedTypes: allowedTypesLabel,
			interpolation: {escapeValue: false},
		}));
		input.setCustomValidity("");
	};

	const removeFile = (index: number) => {
		if (!selectedFiles || !fileInputRef.current) return;

		// DataTransfer is required because FileList is read-only and cannot be directly created or modified.
		// It's the only standard DOM API that allows programmatic creation of FileList objects.
		const dt = new DataTransfer();

		// Filter out the file to remove, then add remaining files
		Array.from(selectedFiles)
			.filter((_, i) => i !== index)
			.forEach(file => dt.items.add(file));

		fileInputRef.current.files = dt.files;
		fileInputRef.current.setCustomValidity("");
		setSelectionNotice(null);
		setSelectedFiles(dt.files.length > 0 ? dt.files : null);
	};

	const acceptAttr = acceptTokens.join(",");

	return (
		<div className="fmdb-file-input-container">
			<input
				ref={fileInputRef}
				type="file"
				id={inputId}
				name={inputName}
				className="fmdb-form-control"
				accept={acceptAttr}
				multiple={multiple}
				required={required}
				onChange={handleFileChange}
			/>

			{selectionNotice && (
				<p className="fmdb-file-selection-note" role="status" aria-live="polite">
					{selectionNotice}
				</p>
			)}

			{selectedFiles && selectedFiles.length > 0 && (
				<div className="fmdb-selected-files">
					<h4 className="fmdb-selected-files-title">{t("selectedFiles")}</h4>
					<ul className="fmdb-file-list">
						{Array.from(selectedFiles).map((file, index) => (
							<li key={file.name} className="fmdb-file-item">
								<div className="fmdb-file-info">
									<span className="fmdb-file-name">{file.name}</span>
									<span className="fmdb-file-size">({formatFileSize(file.size)})</span>
								</div>
								{multiple && (
									<button
										type="button"
										className="fmdb-file-remove"
										onClick={() => removeFile(index)}
										aria-label={`${t("removeFile")} ${file.name}`}
									>
										×
									</button>
								)}
							</li>
						))}
					</ul>
				</div>
			)}
		</div>
	);
}
