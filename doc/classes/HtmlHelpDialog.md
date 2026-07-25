# HtmlHelpDialog

Source: [HtmlHelpDialog.java](../../app/src/main/java/org/example/HtmlHelpDialog.java)

## Purpose

Displays multi-page HTML help content inside a non-modal Swing dialog.

## How It Fits

```mermaid
flowchart LR
    BreathingEditorFrame --> HelpContent
    HelpContent --> HtmlHelpDialog
    HtmlHelpDialog --> HTMLDocument
```

## Collaborators

- [HelpContent](HelpContent.md)

## Key Methods And Utility

- Constructor: Sets up the HTML editor, base URL, footer navigation, and first page.
- `createFooter()`: Builds previous/next/close controls and the page counter.
- `showPage(...)`: Bounds the target page index, updates title/body/buttons, and scrolls to the top.
- `wrapHtml(...)`: Accepts either raw fragments or complete HTML documents.

## Important Invariants

- The dialog is non-modal so users can read help while inspecting the editor.
- Relative help images depend on the `HTMLDocument` base URL provided by `HelpContent`.
- External links are displayed as text instead of launching a browser, keeping the app self-contained and avoiding platform-specific browser handling.

## Maintenance Notes

- Keep help HTML simple. Swing's HTML support is old and does not behave like a modern browser.
