# Logiciel respiration 2D Swing

Source: `Logiciel respiration 2D Swing (7_19_2026 10：04：40 PM).html`

## Objectif

Créer un petit logiciel Java/Swing permettant d’importer un sprite PNG de personnage et de lui appliquer une animation de respiration légère. L’outil vise un usage simple pour une animation idle: placer quelques points d’influence sur une image statique, régler leur mouvement, prévisualiser le résultat, puis exporter des images.

## Principe d’utilisation

1. Charger un sprite PNG.
2. Placer quelques points sur le torse:
   - deux points fixes près des épaules ;
   - deux points sur la cage thoracique ;
   - éventuellement un point sur le ventre ;
   - un point d’ancrage au niveau du bassin.
3. Indiquer quels points doivent bouger.
4. Animer ces points avec une courbe sinusoïdale.
5. Faire suivre légèrement les pixels proches des points animés.

## Interface MVP

```text
+-----------------------------------------------+
| Charger PNG | Lecture | Stop | Export GIF     |
+---------------------------+-------------------+
|                           | Respiration       |
| Sprite                    | Duree : 3,5 s     |
|   o----o                  | Amplitude : 4 px  |
|  /      \                 |                   |
| o        o                | Point selectionne |
|     o                     | X : +2 px         |
|     o                     | Y : -3 px         |
|                           | Rayon : 90 px     |
+---------------------------+-------------------+
| Clic : ajouter | Glisser : deplacer           |
+-----------------------------------------------+
```

## Animation de respiration

La respiration est pilotée par une sinusoïde.

Pour un point situé sur la poitrine:

```java
double phase = Math.sin(time * speed);
double currentX = baseX + offsetX * phase;
double currentY = baseY + offsetY * phase;
```

Exemples:

- inspiration: les points du torse montent légèrement et s’écartent ;
- expiration: ils redescendent et reviennent.

## Déformation par influence des points

Chaque point de contrôle contient:

```java
class ControlPoint {
    double x;
    double y;
    double offsetX;
    double offsetY;
    double radius;
    boolean animated;
}
```

L’influence d’un point sur un pixel peut être calculée avec une courbe douce:

```java
double distance = Math.hypot(pixelX - point.x, pixelY - point.y);
double influence = Math.max(0.0, 1.0 - distance / point.radius);
influence *= influence;
```

Les déplacements produits par tous les points sont additionnés puis normalisés:

```java
double displacementX = 0;
double displacementY = 0;
double totalInfluence = 0;

for (ControlPoint point : points) {
    double distance = Math.hypot(x - point.x, y - point.y);
    double influence = Math.max(0.0, 1.0 - distance / point.radius);
    influence *= influence;

    displacementX += point.currentOffsetX * influence;
    displacementY += point.currentOffsetY * influence;
    totalInfluence += influence;
}

if (totalInfluence > 0) {
    displacementX /= totalInfluence;
    displacementY /= totalInfluence;
}
```

Pour calculer la couleur du pixel de destination, lire le pixel source inversement déplacé:

```java
sourceX = destinationX - displacementX;
sourceY = destinationY - displacementY;
```

Cette technique correspond à une petite déformation par champ d’influence. Elle ne nécessite ni squelette, ni triangulation, ni moteur de skinning.

## Choix technique

Une approche de type maillage triangulé comme Spine est possible, mais Java2D ne fournit pas directement une API simple pour plaquer une texture arbitraire dans un triangle déformé. Il faudrait alors:

- écrire un petit rasteriseur de triangles ;
- découper l’image en bandes ou en cellules ;
- utiliser OpenGL/LWJGL à la place de Swing.

Pour une respiration de quelques pixels, la déformation inverse des pixels est plus simple à développer et donne déjà un résultat correct.

## Architecture Java minimale

```text
BreathingEditorFrame
|-- ToolbarPanel
|-- SpriteEditorPanel
|   |-- BufferedImage originalImage
|   |-- BufferedImage deformedImage
|   `-- List<ControlPoint>
|-- BreathingSettingsPanel
`-- BreathingAnimator

ImageDeformer
|-- deform(...)
|-- calculateDisplacement(...)
`-- bilinearSample(...)
```

L’animation peut utiliser un `javax.swing.Timer`:

```java
Timer timer = new Timer(33, event -> {
    animator.update(System.nanoTime());
    deformedImage = deformer.deform(originalImage, controlPoints);
    spritePanel.repaint();
});
```

`33 ms` correspond à une cible d’environ 30 images par seconde.

## Fonctions du MVP

- Chargement d’un PNG.
- Zoom et déplacement de la vue.
- Ajout, sélection et déplacement de points.
- Points fixes ou animés.
- Réglage du rayon d’influence.
- Déplacement maximal en X et Y.
- Vitesse ou durée de respiration.
- Aperçu lecture/pause.
- Sauvegarde du projet en JSON.
- Export en suite de PNG ou GIF.

## Format de sauvegarde

```json
{
  "image": "personnage.png",
  "duration": 3.5,
  "points": [
    {
      "x": 142,
      "y": 118,
      "offsetX": -3,
      "offsetY": -2,
      "radius": 80,
      "animated": true
    },
    {
      "x": 198,
      "y": 118,
      "offsetX": 3,
      "offsetY": -2,
      "radius": 80,
      "animated": true
    }
  ]
}
```

## Evolution utile: masques

Le principal problème est que le déplacement du torse peut aussi déformer des parties qui devraient rester plus stables, par exemple les bras, les cheveux ou des éléments proches du torse.

Une évolution utile serait d’ajouter un pinceau permettant de peindre une zone déformable. Les pixels noirs du masque restent fixes et les pixels blancs peuvent suivre les points.

Formule conceptuelle:

```text
Image originale + Masque de deformation + Points de controle = Image respirante
```

## Verdict

Pour un outil de création destiné à produire une petite animation idle, Swing est suffisant pour l’interface et l’aperçu. La solution raisonnable pour le MVP est un système de points d’influence avec déformation douce des pixels, plutôt qu’un système complet d’os, de poids et de maillages.
