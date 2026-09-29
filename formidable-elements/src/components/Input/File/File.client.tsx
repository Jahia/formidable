import {type ChangeEvent, useEffect, useRef, useState} from 'react';
import {formatFileSize} from '~/utils/fileUtils';
import {useTranslation} from "react-i18next";
import type {FileTypes} from '~/utils/fileTypes.server';
import {accepts, buildAcceptAttr, getDisplayFormats, isRestricted} from './fileAccept';

interface FileInputProps {
	inputId: string;
	inputName: string;
	/** What the field accepts, from the engine: the types the server lets through and their extensions (Tika's). */
	fileTypes?: FileTypes;
	multiple?: boolean;
	required?: boolean;
	describedBy?: string;
	validationAttributes?: Record<string, string | undefined>;
}

const extensionFromName = (fileName: string): string => {
	const dotIndex = fileName.lastIndexOf(".");
	return dotIndex >= 0 ? fileName.slice(dotIndex).toLowerCase() : fileName;
};

const deduplicateFiles = (files: File[]): File[] => {
	const seen = new Set<string>();
	return files.filter(file => {
		const key = `${file.name}-${file.size}-${file.lastModified}`;
		if (seen.has(key)) {
			return false;
		}

		seen.add(key);
		return true;
	});
};

export default function FileInput(
	{
		inputId,
		inputName,
		fileTypes = {shown: {}, recognised: {}},
		multiple,
		required,
		describedBy,
		validationAttributes
	}: Readonly<FileInputProps>
) {
	const [selectedFiles, setSelectedFiles] = useState<FileList | null>(null);
	const [selectionNotice, setSelectionNotice] = useState<string | null>(null);
	const fileInputRef = useRef<HTMLInputElement>(null);
	const {t} = useTranslation('formidable-elements', {keyPrefix: 'fmdb_inputFile'});
	const allowedTypesLabel = getDisplayFormats(fileTypes)
		.map(format => `"${format}"`)
		.join(", ");

	// The chip list is React state: a native form reset — the Reset button, or the form
	// coming back after a successful submission — empties the input but not the list,
	// and the visitor would believe the previous file is still attached (#288).
	useEffect(() => {
		const formElement = fileInputRef.current?.form;
		if (!formElement) {
			return;
		}

		const handleReset = () => {
			fileInputRef.current?.setCustomValidity("");
			setSelectionNotice(null);
			setSelectedFiles(null);
		};
		formElement.addEventListener('reset', handleReset);
		return () => formElement.removeEventListener('reset', handleReset);
	}, []);

	const syncInputFiles = (files: File[]) => {
		if (!fileInputRef.current) return;

		const dt = new DataTransfer();
		files.forEach(file => dt.items.add(file));
		fileInputRef.current.files = dt.files;
		setSelectedFiles(dt.files.length > 0 ? dt.files : null);
	};

	const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
		const input = event.currentTarget;
		const newFiles = Array.from(input.files ?? []);

		if (newFiles.length === 0) {
			if (!multiple || !selectedFiles || selectedFiles.length === 0) {
				input.setCustomValidity("");
				setSelectionNotice(null);
				setSelectedFiles(null);
			}

			return;
		}

		const previousFiles = multiple && selectedFiles ? Array.from(selectedFiles) : [];

		if (!isRestricted(fileTypes)) {
			const merged = deduplicateFiles([...previousFiles, ...newFiles]);
			syncInputFiles(merged);
			input.setCustomValidity("");
			setSelectionNotice(null);
			return;
		}

		const validFiles = newFiles.filter(file => accepts(file, fileTypes));
		const invalidFiles = newFiles.filter(file => !validFiles.includes(file));

		if (invalidFiles.length === 0) {
			const merged = deduplicateFiles([...previousFiles, ...validFiles]);
			syncInputFiles(merged);
			input.setCustomValidity("");
			setSelectionNotice(null);
			return;
		}

		const invalidFormats = Array.from(new Set(invalidFiles.map(file => extensionFromName(file.name))))
			.map(format => `"${format}"`)
			.join(", ");
		const invalidKey = invalidFiles.length > 1 ? "multipleInvalidFiles" : "singleInvalidFile";
		// no type left: the field accepts no file, and there is no list of formats to give
		const blockingMessage = allowedTypesLabel === "" ? t("noAcceptedType") : t(invalidKey, {
			invalidFormats,
			allowedTypes: allowedTypesLabel,
			interpolation: {escapeValue: false},
		});
		if (validFiles.length === 0 && previousFiles.length === 0) {
			syncInputFiles([]);
			setSelectionNotice(null);
			input.setCustomValidity(blockingMessage);
			input.reportValidity();
			return;
		}

		const merged = deduplicateFiles([...previousFiles, ...validFiles]);
		syncInputFiles(merged);
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

	const acceptAttr = buildAcceptAttr(fileTypes);

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
				aria-describedby={describedBy}
				onChange={handleFileChange}
				{...(validationAttributes ?? {})}
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
