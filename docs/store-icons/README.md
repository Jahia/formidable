# Jahia App Store icons

The icons of the Formidable modules on the Jahia App Store, one per module, in one family. They
are uploaded on each module's entry of the store (the `icon` of the entry, shown in **Modules
and Extensions > Available modules** in Jahia); the entries currently show the generic "Forms"
tiles. Nothing in the modules reads these files.

| Module | Tile | Glyph |
|---|---|---|
| formidable-elements | ![](formidable-elements.png) | a form: label, field, button |
| formidable-engine | ![](formidable-engine.png) | a cog |
| formidable-extended-inputs | ![](formidable-extended-inputs.png) | three sliders |
| formidable-jexperience-engine | ![](formidable-jexperience-engine.png) | an activity pulse |

## The family

- The tile of the store: a 125 × 125 rounded square (radius 22), in Formidable's own colour —
  the gold of its Page Builder boxes and step navigation, `#a8945f`.
- The glyph: white, drawn on a 24-unit grid like every Formidable content-type icon (Lucide
  conventions: stroke 2, round caps and joins, no fill), scaled 3.5 times and centred. Hand-drawn
  SVG, no icon library.

## Regenerating a PNG from its SVG

The SVG is the source; the PNG is what the store takes.

```bash
cd docs/store-icons
convert -background none -density 384 formidable-elements.svg -resize 125x125 formidable-elements.png
```
