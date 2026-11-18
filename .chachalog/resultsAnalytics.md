---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added an Analytics view to the Results page

A form selected on the Results page now has two tabs, **Submissions** and **Analytics**. The analytics cover a period you choose (the last 7, 30 or 90 days, the last 12 months, or all the submissions) and show:

- the number of submissions, their daily average and their distribution over time;
- the submissions per page, for a form placed on several pages, and per language;
- the fields left empty, the least filled first, as a share of the submissions that showed them: a field hidden by its conditional display is not counted as empty;
- the answers of the choice fields (a pie for a single choice of up to six answers, bars otherwise), of the yes/no fields, and of the ratings and scales with their average.

The figures can be exported as CSV or JSON. They are computed in the browser from the submissions the user may already read: no new permission, and up to the 10,000 most recent submissions of the period.
