# Dependency Decisions

This project uses two distinct dependency strategies in its Java modules:

- `provided` for APIs and platform libraries that are expected to be supplied by Jahia at runtime
- embedded compile-scope libraries only when the module must carry its own isolated implementation

## `formidable-engine`

### Embedded libraries

- `org.apache.tika:tika-core`
  - embedded intentionally in the OSGi bundle
  - used for content-only MIME detection during file upload validation
  - kept isolated from Jahia's platform-provided Tika line through OSGi bundle class loading

### Provided libraries

- `org.jahia.server:jahia-impl`
  - declared with global transitive exclusions
  - used only as the anchor API dependency for Jahia server types
- `javax.jcr:jcr`
- `javax.servlet:javax.servlet-api`
- `org.slf4j:slf4j-api`
- `org.osgi:osgi.cmpn`
- `org.osgi:osgi.annotation`
- `org.json:json`
- `com.sun.mail:javax.mail`
- `javax.activation:activation`
- `org.springframework:spring-context-support`
- `commons-lang:commons-lang`
- `commons-fileupload:commons-fileupload`
- `commons-io:commons-io`
- `org.apache.commons:commons-text`

### Notes

- `commons-io` is intentionally kept as `provided`, even though `tika-core` can depend on it transitively.
- `slf4j-api` is excluded from `tika-core` transitives because the platform already provides the API.
- The module uses `maven-dependency-plugin:analyze-only` to keep the declared dependency surface explicit.

### OSGi SPI surface

- Only `org.jahia.modules.formidable.engine.api` is exported from the bundle.
- That package is the public SPI for third-party action implementations and currently contains:
  - `FormAction`
  - `FormActionException`
  - `SubmittedFile`
- All other `org.jahia.modules.formidable.engine.*` packages are internal implementation details with no compatibility promise.

### Technical debt: split SPI and runtime bundles

- Current state:
  - `formidable-engine` still ships both the public SPI (`org.jahia.modules.formidable.engine.api`) and the runtime implementation (servlet, pipeline, built-in actions, config service).
  - External modules should depend only on the SPI package, but they still wire to the same OSGi bundle as the runtime.
- Why this matters:
  - deploying a new `formidable-engine` bundle can refresh modules that import the SPI, even when only runtime internals changed
  - the SPI cannot be versioned independently from the engine implementation
  - the exported package boundary is now clean, but the bundle boundary is still broader than necessary
- Target architecture:
  - `formidable-api`
    - contains only stable SPI types such as `FormAction`, `FormActionException`, `SubmittedFile`, and future public DTOs
    - intended as the compile-time dependency for external modules
  - `formidable-engine`
    - contains only runtime implementation
    - should no longer export the action SPI
- Decision:
  - do not split now
  - keep the current package-level SPI isolation as the intermediate step
  - revisit the split only if Formidable gains multiple external consumers or if OSGi refresh cascades become an operational issue

## `jahia-test-module/formidable-test-module-jsp`

### Provided libraries

- `org.jahia.server:jahia-impl`
  - declared with global transitive exclusions
- `org.jahia.server:jahia-taglib`
- `javax.servlet:jstl`
- `javax.servlet:javax.servlet-api`

### Notes

- This module is JSP-only, so some dependencies are used by JSP/taglib resources rather than Java source imports.
