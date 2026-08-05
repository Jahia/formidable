# Spec: logique conditionnelle avec `logicId` + `fieldKey` / `sourceFieldKey`

## Objectif

Faire évoluer le modèle de logique conditionnelle pour stocker explicitement:

- `logicId`: identifiant stable de la règle
- `fieldKey`: identifiant métier stable porté par chaque champ
- `sourceFieldKey`: clé du champ source référencé par la règle

Le but est de supprimer l'ambiguïté actuelle lors de la création d'une règle quand plusieurs champs sources ont:

- le même label visible
- le même nom technique
- ou un UUID JCR non fiable après duplication / import / copy-paste

## Décision

Le modèle cible n'utilise pas l'UUID JCR comme identité fonctionnelle du champ source.

Le modèle cible repose sur:

1. `logicId` pour identifier la règle
2. `fieldKey` pour identifier un champ dans le modèle métier
3. `sourceFieldKey` dans la règle pour désigner le champ source

## Pourquoi ne pas utiliser `sourceFieldId`

Un `sourceFieldId` basé sur l'identifiant JCR pose un problème structurel:

- l'UUID peut changer lors d'un import
- l'UUID peut changer lors d'une duplication
- l'UUID ne représente pas une identité métier stable

Donc:

- `sourceFieldId` n'est pas le bon pivot fonctionnel
- l'UUID JCR peut rester un détail technique utile au runtime
- mais il ne doit pas être la clé métier stockée dans la règle

## Pourquoi garder `logicId`

`logicId` reste l'identité de la règle.

Il sert à:

- identifier une règle dans la propriété `logics`
- nommer le noeud technique `logicsSrc/{logicId}`
- garder la même règle même si son champ source est modifié
- maintenir la liaison entre le JSON de la règle et le noeud weakref associé

### Règle de stabilité

Si le contributeur modifie une règle existante pour viser une autre source:

- `logicId` est conservé
- `sourceFieldKey` est mis à jour
- la weakref de `logicsSrc/{logicId}` est repointée vers le nouveau champ source

Autrement dit:

- `logicId` identifie la ligne de règle
- `sourceFieldKey` identifie le champ source choisi par cette ligne

## Pourquoi introduire `fieldKey`

Chaque champ doit porter une clé métier persistée, indépendante du repository:

- `fieldKey`

Cette clé:

- est créée une fois
- est persistée sur le noeud JCR du champ
- ne dépend pas du `name`
- ne dépend pas du label visible
- ne dépend pas de l'UUID JCR

## Sémantique

### Sur le champ

Le champ porte:

- `fieldKey`

### Dans la règle

La règle porte:

- `sourceFieldKey`

`sourceFieldKey` doit contenir la valeur du `fieldKey` du champ source.

Donc:

- `fieldKey` et `sourceFieldKey` portent la même valeur
- mais pas dans le même contexte

## Modèle de données cible

### Sur chaque champ source éligible

Ajouter une propriété persistée:

- `fieldKey`

Cette propriété doit exister sur tous les types de champs pouvant être utilisés comme source de logique conditionnelle, et idéalement sur tous les champs de formulaire pour garder un modèle homogène.

### Sur chaque règle de logique conditionnelle

Chaque entrée de `logics` doit contenir:

- `logicId`
- `sourceFieldKey`
- `sourceFieldName`
- `sourceFieldType`
- `operator`
- `value` ou `values`

### Statut des champs

- `logicId`: obligatoire dans le modèle cible
- `sourceFieldKey`: obligatoire dans le modèle cible
- `sourceFieldName`: conservé comme métadonnée lisible et fallback de migration

## Constat sur l'existant

Aujourd'hui:

- le composant React d'édition génère un `logicId`
- la règle JSON stocke surtout `sourceFieldName`
- `FormLogicSyncService` recrée ou maintient `logicsSrc/{logicId}`
- la weakref `logicNodeSource` permet de retrouver le champ source même après renommage

Ce modèle est robuste une fois la weakref créée, mais il reste ambigu au moment de la création initiale:

- deux sources peuvent partager le même label visible
- deux sources peuvent partager le même nom
- le payload initial ne porte pas d'identité métier explicite du champ source

## Comportement attendu dans le Content Editor

### Valeur interne du dropdown

Le dropdown des champs source doit:

- utiliser `sourceFieldKey` comme valeur interne de sélection
- ne plus utiliser `source.name`
- ne pas utiliser l'UUID JCR comme clé métier persistée

### Affichage des labels

Le label visible peut rester basé sur le label fonctionnel du champ.

Si plusieurs sources ont le même label visible:

- chaque occurrence reçoit un suffixe `:N`
- la numérotation commence à `1`

Exemple:

- `Select an option:1`
- `Select an option:2`
- `Select an option:3`

La numérotation est basée sur l'ordre d'apparition dans la liste affichée.

### Création d'une règle

Lorsqu'un contributeur choisit une source:

- le composant éditeur génère ou réutilise le `logicId`
- il lit le `fieldKey` du champ source choisi
- il stocke ce `fieldKey` dans `sourceFieldKey`
- il stocke aussi `sourceFieldName` et `sourceFieldType`

### Modification d'une règle

Si le contributeur change la source d'une règle existante:

- `logicId` ne change pas
- `sourceFieldKey` est remplacé par la nouvelle valeur
- `sourceFieldName` et `sourceFieldType` sont mis à jour

## Contrainte métier explicite

Le Content Editor retire déjà une source de la liste une fois qu'elle est utilisée dans une règle du même champ cible.

Cette contrainte doit être conservée:

- pour un champ cible donné, un `sourceFieldKey` ne peut apparaître qu'une seule fois
- une seconde règle visant le même `sourceFieldKey` n'est pas un cas métier supporté
- l'unicité est portée par `sourceFieldKey`

## Résolution backend

### Principe

Le backend ne doit plus considérer `sourceFieldName` comme clé primaire de résolution.

Ordre de résolution cible:

1. `sourceFieldKey`
2. fallback sur weakref existante liée à `logicId`
3. fallback sur `sourceFieldName` pour compatibilité ascendante uniquement

### Effet attendu dans `FormLogicSyncService`

Le service de sync doit:

- lire chaque entrée `logics`
- garantir la présence d'un `logicId`
- garantir ou réparer la présence d'un `sourceFieldKey`
- résoudre le champ source par `sourceFieldKey`
- créer ou mettre à jour `logicsSrc/{logicId}`
- positionner `logicNodeSource` vers le noeud correspondant

### Source de vérité

Si `sourceFieldKey` est présent et valide:

- il fait foi
- `sourceFieldName` n'est plus utilisé pour choisir la source

Si `sourceFieldKey` est absent ou invalide:

- le service peut s'appuyer sur la weakref existante de `logicsSrc/{logicId}`
- sinon il peut retomber sur `sourceFieldName` dans un mode legacy

## Comportement de `logicsSrc`

Le modèle conserve `logicsSrc`:

- un noeud enfant par `logicId`
- une weakref `logicNodeSource` pointant vers le champ source

### Pourquoi le conserver

`logicsSrc` reste utile pour:

- le renommage des champs
- la robustesse côté JCR
- la récupération du noeud source réel côté runtime
- la compatibilité avec la logique déjà en place

Le changement demandé ne remplace donc pas `logicsSrc`; il fiabilise l'identité utilisée avant la création de la weakref.

## Runtime et soumission

### Runtime front public

Le runtime public peut continuer à exploiter la weakref résolue via `logicsSrc`.

L'UUID JCR peut être injecté au runtime comme détail technique, sans devenir la clé métier stockée dans la règle.

### Pipeline de soumission

Le pipeline serveur peut continuer à exploiter:

- `logicId`
- la weakref associée

`sourceFieldKey` sert d'abord à:

- fiabiliser l'authoring
- fiabiliser la première résolution backend
- rendre la règle stable face aux opérations de duplication/import

## Création et maintenance de `fieldKey`

### Définition précise

`fieldKey` est une propriété JCR technique portée par le noeud du champ.

Caractéristiques attendues:

- type: `string`
- visibilité: cachée pour le contributeur
- création: auto-créée
- édition manuelle: interdite
- contenu: un UUID aléatoire au format texte standard

Exemple de forme attendue:

- `550e8400-e29b-41d4-a716-446655440000`

Le principe recherché est le même que pour les identifiants techniques générés automatiquement par Jahia: une valeur opaque, non métier, unique, stable tant qu'on ne décide pas explicitement de la remapper.

### Création

Tout champ nouvellement créé doit recevoir un `fieldKey`.

La valeur initiale doit être générée automatiquement une seule fois, sous forme d'UUID texte.

### Lecture

Le Content Editor et le backend doivent pouvoir lire ce `fieldKey` sans calcul dérivé.

### Unicité

Le `fieldKey` doit être unique dans le périmètre d'un formulaire.

En pratique, la génération UUID doit rendre les collisions négligeables; si une stratégie de remappage sur duplication est ajoutée, elle doit continuer à garantir cette unicité.

## Duplication, copy-paste, import

### Exigence produit

L'option voulue doit survivre correctement aux opérations qui cassent l'UUID JCR:

- duplication
- copy-paste
- import

### Règle de conception

Le `fieldKey` est la référence stable utilisée pour reconnecter les règles aux champs après ces opérations.

### Duplication d'un sous-arbre cohérent

Lorsqu'un sous-arbre contenant:

- des champs sources
- des champs cibles
- des règles

est dupliqué, les règles dupliquées doivent être réparées pour pointer vers les nouveaux `fieldKey` internes du sous-arbre copié, selon la stratégie de duplication retenue.

### Point à cadrer

Il faut définir explicitement le comportement de duplication de `fieldKey`.

Deux stratégies existent:

1. conserver le `fieldKey` sur copie
2. régénérer le `fieldKey` sur copie

La présente spec retient la cible produit suivante:

- `fieldKey` doit rester stable d'un point de vue métier
- mais il ne doit pas créer de collision entre l'original et la copie dans un même formulaire

Donc la duplication doit inclure une stratégie explicite de remappage des `fieldKey` et des `sourceFieldKey` si la copie coexiste avec l'original.

Ce point doit être traité comme une règle de duplication dédiée, pas comme un détail implicite.

## Migration et compatibilité ascendante

### Règles existantes

Les règles existantes peuvent ne contenir que:

- `logicId`
- `sourceFieldName`

### Champs existants

Les champs existants peuvent ne pas avoir de `fieldKey`.

### Stratégie de migration

1. ajouter un `fieldKey` à tous les champs existants qui n'en ont pas
2. pour chaque règle existante:
   - récupérer la source via la weakref `logicsSrc/{logicId}` si disponible
   - sinon fallback sur `sourceFieldName`
3. écrire `sourceFieldKey` à partir du `fieldKey` du champ source résolu
4. conserver `sourceFieldName` comme secours lisible

### Objectif de migration

Après migration ou resynchronisation:

- chaque champ doit avoir un `fieldKey`
- chaque règle active doit avoir `logicId` et `sourceFieldKey`

## Impacts attendus par couche

### Modèle JCR

- ajouter `fieldKey` comme propriété technique persistée sur les champs

### Content Editor React

- charger `fieldKey` dans les options source
- utiliser `sourceFieldKey` comme valeur du dropdown
- désambiguïser les labels dupliqués avec suffixe `:N`
- écrire `logicId` + `sourceFieldKey` dans la règle

### GraphQL côté editor

- exposer `fieldKey`
- conserver `displayName`, `name`, `path`, `type` pour l'affichage et les métadonnées

### Java sync

- résoudre d'abord par `sourceFieldKey`
- continuer à alimenter `logicsSrc/{logicId}`
- ne garder `sourceFieldName` qu'en fallback legacy

### Runtime / rendu

- aucun changement de principe sur l'exploitation de `logicsSrc`
- l'UUID JCR peut rester un détail technique de runtime

## Critères d'acceptation

### Authoring

- deux sources avec le même label sont affichées de manière distincte
- la sélection d'une source ambiguë reste stable après sauvegarde
- changer de source ne régénère pas le `logicId`

### Persistance

- une règle nouvellement créée contient `logicId` et `sourceFieldKey`
- le champ source porte un `fieldKey`
- `logicsSrc/{logicId}` pointe vers le bon noeud source dès la première sync

### Compatibilité

- une ancienne règle sans `sourceFieldKey` continue de fonctionner
- une ancienne règle peut être enrichie automatiquement avec `sourceFieldKey`
- un ancien champ sans `fieldKey` peut être réparé

### Robustesse

- le renommage d'un champ source ne casse pas une règle existante
- deux champs homonymes ne conduisent plus à une résolution arbitraire
- duplication/import/copy-paste ne doivent plus dépendre de l'UUID JCR comme identité métier

## Résumé

Le modèle cible donne un rôle clair à chaque identifiant:

- `logicId`: identité de la règle
- `fieldKey`: identité métier du champ
- `sourceFieldKey`: référence métier du champ source dans la règle

Le système conserve `logicsSrc` et la weakref JCR comme mécanisme technique, mais l'identité fonctionnelle ne repose plus sur `sourceFieldName` ni sur l'UUID JCR.
