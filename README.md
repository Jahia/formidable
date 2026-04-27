# formidable-modules


## What is Formidable? 
Formidable is the new solution to manage Forms with Jahia. It will fully replace Jahia Forms. It is currently in development. 

## Main architecture principles
Formidable is based on Jahia standard technologies:
- Forms, steps, fieldset and fields are regular content items
- Rendering / views are using JavaScript modules, they're built in React / TSX
- Actions can be declared in Java / OSGi, as per any other action inside Jahia

## Alpha version

### Scope

Alpha version is targeted for June 2026. 
It will include the ability to: 
- Create forms, with multisteps
- 8 field types:
-- Text input
-- Text area
-- Email
-- Select (Dropdown)
-- Radio
-- Date
-- Datetime
-- File upload
-- Color
- 2 actions:
-- Send to jCustomer
-- Send by email
- Support for Captcha (Tested with Google Recaptcha and Cloudflare Turnstile)
- Extension points:
-- Create your own action
-- Overwrite the view for a field type
-- Create a new field type

### Packaging 
- 2 modules:
-- formidable-elements, that provides the fields to build Forms with
-- formidable-engine, that provides the framework for captcha and actions

### Current known limitations 
- Formidable is depending on jContent 3.7 that is not yet released. We're trying to work on that limitation to make Formidable compatible with jContent 3.6.
- When selecting a field, users don't know what it will look like. This pain point will be adressed globally inside Jahia in 2026.
- Forms are currently built using jContent list or structured view. There is no support for Page Builder / visual building. This issue should be resolved over the summer.
