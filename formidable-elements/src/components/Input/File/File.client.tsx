import {type ChangeEvent, useRef, useState} from 'react';
import {formatFileSize} from '~/utils/fileUtils';
import {useTranslation} from "react-i18next";

interface FileInputProps {
	inputId: string;
	inputName: string;
	accept?: string;
	multiple?: boolean;
	required?: boolean;
}

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
	const {t} = useTranslation('formidable', {keyPrefix: 'fmdb_inputFile'});

	const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
		setSelectedFiles(event.target.files);
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
		setSelectedFiles(dt.files);
	};

	return (
		<div className="fmdb-file-input-container">
			<input
				ref={fileInputRef}
				type="file"
				id={inputId}
				name={inputName}
				className="fmdb-form-control"
				accept={accept}
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
