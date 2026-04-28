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
	"text/plain": [".txt"],
};

const formatAcceptToken = (token: string): string => {
	const loweredToken = token.toLowerCase();
	if (loweredToken.startsWith(".")) {
		return loweredToken;
	}

	return MIME_EXTENSION_MAP[loweredToken]?.join(", ") ?? token;
};

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
		return loweredType.startsWith(prefix);
	}

	if (loweredType === loweredToken) {
		return true;
	}

	const knownExtensions = MIME_EXTENSION_MAP[loweredToken];
	return !!knownExtensions && knownExtensions.some(extension => loweredName.endsWith(extension));
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
	const fileInputRef = useRef<HTMLInputElement>(null);
	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_inputFile'});
	const acceptTokens = normalizeAccept(accept);
	const allowedTypesLabel = acceptTokens.map(formatAcceptToken).join(", ");

	const syncInputFiles = (files: File[]) => {
		if (!fileInputRef.current) return;

		const dt = new DataTransfer();
		files.forEach(file => dt.items.add(file));
		fileInputRef.current.files = dt.files;
		setSelectedFiles(dt.files);
	};

	const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
		const input = event.currentTarget;
		const files = Array.from(input.files ?? []);

		if (files.length === 0) {
			input.setCustomValidity("");
			setSelectedFiles(null);
			return;
		}

		if (acceptTokens.length === 0) {
			input.setCustomValidity("");
			setSelectedFiles(input.files);
			return;
		}

		const validFiles = files.filter(file => acceptTokens.some(token => matchesAcceptToken(file, token)));
		const invalidFiles = files.filter(file => !validFiles.includes(file));

		if (invalidFiles.length === 0) {
			input.setCustomValidity("");
			setSelectedFiles(input.files);
			return;
		}

		syncInputFiles(validFiles);

		const invalidFormats = Array.from(new Set(invalidFiles.map(file => extensionFromName(file.name))))
			.map(format => `"${format}"`)
			.join(", ");
		const message = t(validFiles.length > 0 || invalidFiles.length > 1 ? "multipleInvalidFiles" : "singleInvalidFile", {
			invalidFormats,
			allowedTypes: allowedTypesLabel,
			interpolation: {escapeValue: false},
		});
		const hasValidSelection = validFiles.length > 0;

		input.setCustomValidity(hasValidSelection ? "" : message);
		input.reportValidity();
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
		setSelectedFiles(dt.files);
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
