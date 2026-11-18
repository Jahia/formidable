# Results analytics

The Results page shows a selected form in two tabs: **Submissions** (the table and the detail panel) and
**Analytics**, a report computed on the submissions of a period.

## What the report holds

| Section | What it counts | Read from |
|---|---|---|
| Figures | Submissions of the period, daily average, number of pages, last submission | `jcr:created`, `referer` |
| Submissions over time | One column per day, per month past 92 days, the empty days kept | `jcr:created`, in the reader's time zone |
| Submissions per page | One row per page the form was submitted from | `referer` |
| Submissions per language | One row per language | `locale` |
| Fields left empty | Per field, the submissions that left it empty, as a share of the submissions it was shown in, the least filled first | `data`, `files`, the form's `logics` |
| Answers | Per choice, yes/no and scale field, the count and share of each answer | `data`, the form's `options` |

**The page of a submission** is its `Referer` header reduced to one key per page: the host, the query, the
context path, the `/cms/render/<workspace>` prefix, the language segment, the `/sites/<site>` prefix and the
`.html` suffix are removed, so a page reached in two languages, with a tracking parameter or through the site's
domain counts once. The language segment is removed only when it is the submission's own `locale`. A submission
without the header is counted under **Unknown**.

**The fields** are those the form declares that submit a value (`fmdbmix:submittableField`) or a file
(`fmdbmix:fileField`), in the form's order, without the sensitive ones (`fmdbmix:sensitiveField`). A name the
submissions hold and the form no longer declares (a field since removed, a form since deleted) is reported too,
under its stored name, and never as left empty: only the submissions that answered it are known to have seen it.

**A field hidden by its conditional display was not left empty.** For each submission the page reads the form's
conditional logic back (`analytics.logic.ts`), as the engine judges it at submission
(`ConditionalLogicEvaluator`): the rules of one element all have to hold, an element inside a hidden fieldset or
step is hidden, and a rule on a hidden source fails. A conditional field is then counted on the submissions
that filled it or whose values prove it was displayed, and its row says so ("shown in 2 of 3 submissions").

| Rule on | Judged from |
|---|---|
| A field | The submitted values of that field, with the engine's operators |
| A URL parameter | The query of the page the submission came from (`referer`) |
| A cookie, a JavaScript variable, a profile property, a date compared with today | Not recorded with the submission: the submission is left out of that field's count, unless it filled the field |

The rules are those of the form as it stands: a submission older than a change of the logic is judged by the
new rules.

**The answers** follow the field's marker mixin:

| Marker | Report |
|---|---|
| `fmdbmix:choiceField` | Every declared option in the form's order, the unchosen ones included, then the stored answers the form no longer offers. Shares are counted on the submissions that answered. A pie when a submission holds one answer and at most six answers were chosen; bars otherwise, since the shares of a multiple choice add up to more than 100%. |
| `fmdbmix:booleanField` | Yes and No, counted on all the submissions: an unchecked box stores nothing. |
| `fmdbmix:numberField` | The average. A breakdown per answer only when the answers are whole numbers from 0 to 10 (a rating, a scale). |

The options of a choice field fed by a source (a declared source, categories, contents) are not stored on the
form: its answers show under their stored value.

## How it is computed

Everything is computed in the browser, in `FormResults/analytics/analytics.utils.ts` (pure functions, unit
tested), from the submissions the reader may already read on the page. There is no server code and no new
permission: the report reads `fmdb:formSubmission` nodes in live through the same JCR-SQL2 query as the table,
with a lighter selection (`GET_SUBMISSIONS_FOR_ANALYTICS`), 200 at a time, outside the Apollo cache.

A period holds at most the 10,000 most recent submissions; past that the page says how many it analysed. The
default period is the last 30 days, so that opening the tab on a form with years of results stays fast.

```
FormResults/analytics/
├── index.ts                  Barrel
├── AnalyticsPanel.tsx        Loads the period, lays the report out
├── analytics.utils.ts        Parsing and aggregation
├── analytics.utils.test.ts
├── analytics.logic.ts        Whether a field was shown, from the conditional logic
├── analytics.export.ts       CSV and JSON of the report
└── charts.tsx                Bars, pie, timeline (Moonstone has no chart)
```

## Export

**Export CSV** writes one row per figure (section, field, item, count, share), UTF-8 with a byte order mark,
CRLF line ends, every cell quoted, and a leading quote before a cell that a spreadsheet would read as a
formula. **Export JSON** writes the report as computed, with the form, the site, the period and the time it
was produced. The submissions themselves are exported by the page's **Export** button ([Export](export.md)).
