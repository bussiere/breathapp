# Class Index

| Class | Responsibility |
| --- | --- |
| [AnimatedPngWriter](classes/AnimatedPngWriter.md) | Writes animated PNG/APNG files by assembling PNG chunks and animation control chunks without relying on third-party encoders. |
| [AnimationExporter](classes/AnimationExporter.md) | Turns a source sprite plus breathing controls into exported frame sequences, spritesheets, APNG, GIF, and atlas metadata. |
| [AnimationPreviewPanel](classes/AnimationPreviewPanel.md) | Displays rendered animation frames in the Swing preview card with checkerboard transparency support. |
| [App](classes/App.md) | Application entry point; either runs the GUI or dispatches the headless demo export command. |
| [BreathingAnimator](classes/BreathingAnimator.md) | Maintains playback timing and converts wall-clock time into a sinusoidal breathing phase. |
| [BreathingEditorFrame](classes/BreathingEditorFrame.md) | Main Swing window and workflow coordinator for loading images, editing controls, previewing, saving, exporting, and tutorial handling. |
| [BreathingProject](classes/BreathingProject.md) | Portable project model for JSON save/load, including embedded image bytes and all control metadata. |
| [CheckerPaints](classes/CheckerPaints.md) | Small paint factory for checkerboard backgrounds used behind transparent sprites. |
| [ControlPoint](classes/ControlPoint.md) | Mutable point control that defines a circular animated or locked influence area on the sprite. |
| [ControlStroke](classes/ControlStroke.md) | Mutable free-line control that defines a polyline animated or locked influence area on the sprite. |
| [DemoExport](classes/DemoExport.md) | Headless demo exporter used by scripts, packaging smoke tests, and reproducible example outputs. |
| [EditorAnimationPreviewRenderer](classes/EditorAnimationPreviewRenderer.md) | Background renderer for full animation previews so the UI can stay responsive while frames are generated. |
| [EditorExportDialogs](classes/EditorExportDialogs.md) | File chooser helpers for selecting export targets and batch export formats. |
| [EditorExportService](classes/EditorExportService.md) | Application-level export facade that renders frames and writes user-selected output formats. |
| [EditorFilePaths](classes/EditorFilePaths.md) | Path utility for adding extensions without duplicating existing suffixes. |
| [EditorImageControlBounds](classes/EditorImageControlBounds.md) | Validates and clamps control coordinates when the current sprite image changes size. |
| [EditorImageLoader](classes/EditorImageLoader.md) | Loads PNGs or project-embedded images and normalizes them to ARGB for deformation. |
| [EditorLivePreview](classes/EditorLivePreview.md) | Manages cancellable background deformation for the live editor preview. |
| [EditorSelectionController](classes/EditorSelectionController.md) | Synchronizes selected controls with side-panel widgets and keeps model invariants reflected in the UI. |
| [EditorSidePanelFactory](classes/EditorSidePanelFactory.md) | Builds the right-side Swing control panel from reusable component/action records. |
| [EditorToolbarFactory](classes/EditorToolbarFactory.md) | Builds the main toolbar and wires toolbar buttons to frame actions. |
| [EditorTutorialSession](classes/EditorTutorialSession.md) | Tracks the temporary state needed to load the Chips tutorial and restore the previous project. |
| [GifSequenceWriter](classes/GifSequenceWriter.md) | Writes animated GIF frames through ImageIO while controlling delay and loop metadata. |
| [HelpContent](classes/HelpContent.md) | Loads JSON-defined help/tutorial pages and resolves resource-relative image paths for Swing HTML rendering. |
| [HtmlHelpDialog](classes/HtmlHelpDialog.md) | Modal Swing dialog that displays paged HTML help content. |
| [ImageDeformer](classes/ImageDeformer.md) | Core pixel deformation engine; blends control influences, honors locked regions, and samples source pixels bilinearly. |
| [JsonSupport](classes/JsonSupport.md) | Shared JSON parsing helpers for fallback values, colors, and formatted output. |
| [LastPathMemory](classes/LastPathMemory.md) | Remembers the last file chooser directory through Java preferences. |
| [PolylineGeometry](classes/PolylineGeometry.md) | Geometry helper for distances between pixels and free-line stroke segments. |
| [RatioControlPreset](classes/RatioControlPreset.md) | Stores control coordinates as image-size ratios so one breathing setup can be applied to sprites with different dimensions. |
| [SpriteEditorPanel](classes/SpriteEditorPanel.md) | Interactive canvas for painting the sprite, selecting controls, drawing strokes, dragging points, zooming, panning, and displaying previews. |
