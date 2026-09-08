# Formidable samples (JavaScript module)

This module shows how a JavaScript module of your own extends Formidable **without changing
Formidable**: it only depends on `formidable-elements` and ships its own definitions, editor
overrides and views. It is deployed on our test instances; nothing in it is meant for production
as is, everything in it is meant to be copied.

One page per sample:

| Sample                                                            | What it shows                                                                                                                                  |
| ----------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| [Two-column fieldset](docs/fieldset-two-columns.md)               | An extra view for a built-in container, picked in the **View** chooser                                                                         |
| [Custom CSS classes on a fieldset](docs/fieldset-custom-style.md) | A mixin adding a property to a built-in type, in an editor section of its own, and a view applying it                                          |
| [Help text position](docs/help-text-position.md)                  | A setting added to every field with a help text, shown right under **Help text**, honoured by a rendering that takes the place of Formidable's |

The rules the samples follow (rendering contracts, what a third-party view must keep, how the
editor form can be shaped) are in
[How to extend Formidable views and elements from a third-party module](../../docs/how-to-extend-views-and-elements-from-third-party-module.md).
