# Architecture

Breath is a desktop Java 21 Swing application. The editor keeps a mutable sprite model in memory, renders live preview frames through the deformation engine, and exports final animation assets through format-specific writers.

## Main Components

```mermaid
flowchart LR
    App[App.main] --> Frame[BreathingEditorFrame]
    Frame --> Canvas[SpriteEditorPanel]
    Frame --> Selection[EditorSelectionController]
    Frame --> Live[EditorLivePreview]
    Frame --> Export[EditorExportService]
    Frame --> Project[BreathingProject]
    Frame --> Tutorial[EditorTutorialSession]
    Canvas --> Point[ControlPoint]
    Canvas --> Stroke[ControlStroke]
    Live --> Deformer[ImageDeformer]
    Export --> AnimationExporter
    AnimationExporter --> Deformer
    AnimationExporter --> APNG[AnimatedPngWriter]
    AnimationExporter --> GIF[GifSequenceWriter]
    Project --> Json[JsonSupport]
    Ratio[RatioControlPreset] --> Point
    Ratio --> Stroke
```

## Startup Flow

```mermaid
sequenceDiagram
    participant User
    participant App
    participant Frame as BreathingEditorFrame
    participant Canvas as SpriteEditorPanel
    participant Side as EditorSidePanelFactory
    participant Toolbar as EditorToolbarFactory

    User->>App: launch application
    App->>Frame: new BreathingEditorFrame()
    Frame->>Canvas: create with point/stroke lists
    Frame->>Side: create side controls
    Frame->>Toolbar: create action toolbar
    Frame->>Canvas: set selection and changed listeners
    Frame->>Frame: start preview timer
```

## Editing And Live Preview

```mermaid
sequenceDiagram
    participant User
    participant Canvas as SpriteEditorPanel
    participant Frame as BreathingEditorFrame
    participant Selection as EditorSelectionController
    participant Live as EditorLivePreview
    participant Deformer as ImageDeformer

    User->>Canvas: click/drag point or stroke
    Canvas->>Frame: controlsChangedListener()
    Frame->>Frame: markProjectDirty()
    Frame->>Live: markDirty()
    Frame->>Selection: showSelectedControl(selection)
    Frame->>Live: refresh(image, controls, phase)
    Live->>Deformer: deform(...)
    Deformer-->>Live: preview image
    Live-->>Canvas: setPreview(...)
```

## Deformation Pipeline

```mermaid
flowchart TD
    Source[BufferedImage source] --> ARGB[ensure ARGB pixels]
    Controls[ControlPoint + ControlStroke] --> Context[FrameContext primitive arrays]
    Context --> Locks[locked point/stroke tests]
    Context --> Influence[weighted influence by radius]
    ARGB --> Rows[warp rows]
    Rows --> Sample[bilinear sample source pixels]
    Locks --> Rows
    Influence --> Rows
    Sample --> Output[deformed BufferedImage]
```

## Export Pipeline

```mermaid
sequenceDiagram
    participant Frame as BreathingEditorFrame
    participant Service as EditorExportService
    participant Exporter as AnimationExporter
    participant Deformer as ImageDeformer
    participant APNG as AnimatedPngWriter
    participant GIF as GifSequenceWriter

    Frame->>Service: export request
    Service->>Exporter: renderFrames(...)
    Exporter->>Deformer: deform each phase
    Deformer-->>Exporter: frames
    Service->>Exporter: write selected format
    Exporter->>APNG: write APNG when requested
    Exporter->>GIF: write GIF when requested
```

## Project Save/Load

```mermaid
flowchart LR
    Frame[BreathingEditorFrame] --> Save[BreathingProject.fromEditorState]
    Save --> Json[JsonSupport]
    Save --> File[project JSON]
    File --> Parse[BreathingProject.parse]
    Parse --> Image[EditorImageLoader]
    Parse --> Point[ControlPoint]
    Parse --> Stroke[ControlStroke]
    Point --> Canvas[SpriteEditorPanel]
    Stroke --> Canvas
```

## Ratio Preset Batch Flow

```mermaid
flowchart LR
    Current[Current controls] --> Preset[RatioControlPreset.fromControls]
    Preset --> Json[ratio preset JSON]
    Json --> Load[RatioControlPreset.load]
    Load --> Apply[applyTo target image]
    Apply --> Batch[EditorExportService.runBatch]
    Batch --> Outputs[GIF / APNG / spritesheet]
```

## Packaging And GitHub Upload

```mermaid
flowchart TD
    BuildScript[build_*_standalone script] --> Package[package_common.py]
    Package --> Gradle[Gradle test + installDist]
    Package --> Version[build-version.properties]
    Package --> JPackage[jpackage app-image]
    Package --> Zip[versioned zip in dist]
    Zip --> Upload[upload_binary_github.py]
    Upload --> Readme[README GitHub URL]
    Upload --> Release[GitHub Release assets]
```
