# Branch Replay Notes

This file documents the recent security-related changes so they can be replayed manually on another branch.

## Scope

The changes covered here are:

1. Remove misleading HTML stripping on input in `FormDataParser`
2. Keep the "escape on output" model explicit
3. Replace the hand-rolled HTML encoder with Apache Commons Text
4. Rename `FieldSanitizer` to `FieldEscaper`
5. Keep Tika MIME allowlist enforcement content-only (`TIKA.detect(data)`)
6. Add a regression test covering HTML escaping at the email sink

## Files changed

- `formidable-engine/pom.xml`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FormDataParser.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FieldSanitizer.java` (deleted)
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FieldEscaper.java` (new)
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/email/SendEmailNotificationFormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/email/SendEmailContentFormAction.java`
- `formidable-engine/src/test/java/org/jahia/modules/formidable/engine/actions/email/EmailActionRecipientTest.java`

## Replay steps

### 1. Add `commons-text` to `formidable-engine/pom.xml`

Add this dependency:

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-text</artifactId>
    <version>1.10.0</version>
    <scope>provided</scope>
</dependency>
```

### 2. Update `FormDataParser`

In `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FormDataParser.java`:

- Remove `HTML_TAG_PATTERN`
- Remove `FREE_TEXT_TYPES`
- Remove `stripHtmlIfFreeText(...)`
- Stop mutating text field values before validation
- Update the class-level Javadoc to describe the real contract:
  - validate on input
  - escape at output sinks
  - do not treat input stripping as XSS protection
- Keep MIME detection content-only:

```java
// Content-only detection for MIME allowlist enforcement.
// Do not pass the filename here: Tika may use it to refine ambiguous types.
String detectedMime = TIKA.detect(data);
```

Also update the `FormFile` record Javadoc so `mimeType` is described as:

```java
detected MIME type (via content-only Tika detection)
```

### 3. Replace `FieldSanitizer` with `FieldEscaper`

Delete:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FieldSanitizer.java`

Create:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FieldEscaper.java`

With this contract:

- `html(String)` uses `StringEscapeUtils.escapeHtml4(...)`
- `headerSafe(String)` strips `\r`, `\n`, `\t` for email-header safety
- `plainText(String)` returns the raw plain-text value unchanged

Implementation used:

```java
package org.jahia.modules.formidable.engine.actions;

import org.apache.commons.text.StringEscapeUtils;

public final class FieldEscaper {

    private FieldEscaper() {}

    public static String html(String value) {
        return StringEscapeUtils.escapeHtml4(value == null ? "" : value);
    }

    public static String headerSafe(String value) {
        if (value == null) return "";
        return value.replaceAll("[\r\n\t]", " ").trim();
    }

    public static String plainText(String value) {
        return value != null ? value : "";
    }
}
```

### 4. Update email actions to use `FieldEscaper`

In:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/email/SendEmailNotificationFormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/email/SendEmailContentFormAction.java`

Replace imports and calls from `FieldSanitizer` to `FieldEscaper`.

Main substitutions:

- `FieldSanitizer.headerSafe(...)` -> `FieldEscaper.headerSafe(...)`
- `FieldSanitizer.htmlEncode(...)` -> `FieldEscaper.html(...)`
- `FieldSanitizer.plainText(...)` -> `FieldEscaper.plainText(...)`

Also rename the helper in `SendEmailContentFormAction`:

- `htmlEncodeJoinedValues(...)` -> `htmlEscapeJoinedValues(...)`

### 5. Add regression test for escape-on-output behavior

In `formidable-engine/src/test/java/org/jahia/modules/formidable/engine/actions/email/EmailActionRecipientTest.java`,
add a test asserting that:

- raw submitted text is preserved on the plain-text path
- HTML output is escaped only when interpolated into an HTML sink

Test added:

```java
@Test
void notificationInterpolationEscapesHtmlAtOutputWithoutMutatingInput() {
    // Given a submitted plain-text value containing HTML-like content,
    // interpolation into an HTML email body must preserve the raw value in memory and escape it only at output.
    String template = "<p>${comment}</p>";
    Map<String, List<String>> parameters = Map.of(
            "comment", List.of("<script>alert(1)</script><!-- note -->")
    );

    // When the notification template is interpolated for an HTML sink.
    String html = SendEmailNotificationFormAction.interpolate(template, parameters, true);
    String plainText = SendEmailNotificationFormAction.interpolate("${comment}", parameters, false);

    // Then the HTML output is escaped, while the plain-text path keeps the original submitted value unchanged.
    assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;&lt;!-- note --&gt;</p>", html);
    assertEquals("<script>alert(1)</script><!-- note -->", plainText);
}
```

## Notes

- `FormidableConfigService` and forward/CAPTCHA timeout configurability were changed separately and are not part of this replay file.
- Verification was not completed in this workspace because local Maven/Java is currently failing with `release version 17 not supported`.
