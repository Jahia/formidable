/* global CKEDITOR */
// CKEditor 4 configuration for the helpText fields of formidable-elements.
// Referenced from the field definitions via
// richtext[ckeditor.toolbar='FormidableHelp',ckeditor.customConfig='$context/modules/formidable-engine/javascript/ckeditor/helpTextConfig.js'].
// Named toolbar pattern: ckeditor.toolbar='FormidableHelp' resolves to
// config.toolbar_FormidableHelp defined below.
// Mirrors the CKEditor 5 helpText toolbar registered by the engine UI extension.
// The Link and Image dialogs keep the Jahia pickers wired by Content Editor.
CKEDITOR.editorConfig = function (config) {
	config.toolbar_FormidableHelp = [
		['Bold', 'Italic', 'Underline'],
		['Image'],
		['Link', 'Unlink'],
		['BulletedList', 'NumberedList']
	];
};
