Diagnostic
Je n’ai pas lancé Cypress. Diagnostic statique du repo tests/cypress et de l’implémentation actuelle.
1.
Le bootstrap de site est incomplet. Dans tests/cypress/support/constants.ts:6, les specs n’activent que formidable-elements. Or l’évolution du projet introduit une dépendance fonctionnelle à formidable-engine pour le submit /modules/formidable-engine/form-submit, les actions, la logique conditionnelle editor-side et le dashboard résultats. Ton constat est correct: il faut activer au minimum les deux modules sur le site de test.
2.
Le helper de création de formulaire n’est plus aligné avec la structure JCR réelle. tests/cypress/support/fixtures/forms.ts:8 injecte les children directement sous fmdb:form. Mais le rendu actuel lit uniquement le nœud fields autocreated, puis ses enfants, dans formidable-elements/src/components/Form/default.server.tsx:44. Donc le builder de fixture est conceptuellement obsolète: les éléments doivent être créés sous fmdb:fieldList (fields), pas à la racine du form.
3.
Les helpers actuels ne permettent pas de tester le vrai flux de soumission. tests/cypress/support/fixtures/forms.ts:22 ouvre le formulaire en preview jContent. Or en preview/edit mode, le submit est explicitement désactivé dans formidable-elements/src/components/Form/default.server.tsx:74. Conséquence: la base actuelle est adaptée au rendu, pas aux tests de soumission, CAPTCHA, auth, actions, error codes.
4.
Le page-object Form est partiellement en drift avec le composant React actuel. Les assertions de message cherchent .fmdb-form-submission et .fmdb-form-error dans tests/cypress/page-object/Form.ts:203, tests/cypress/page-object/Form.ts:208, tests/cypress/page-object/Form.ts:213. Le composant rend maintenant .fmdb-message / .fmdb-message-success / .fmdb-message-error dans formidable-elements/src/components/Form/Form.client.tsx:275. Ces helpers ne reflètent donc plus l’UI actuelle.
5.
Les helpers d’interaction texte ne déclenchent pas les événements dont dépend le runtime moderne. tests/cypress/page-object/elements/TextInput.ts:8 et tests/cypress/page-object/elements/TextareaInput.ts:11 font un simple invoke('val', ...). Mais la logique conditionnelle du formulaire écoute input et change dans formidable-elements/src/components/Form/Form.client.tsx:110. Donc les futurs tests de logique conditionnelle, multi-step, validation et visibilité seront faux ou flaky avec ces page-objects.
6.
La couverture E2E est très en retard sur la surface du projet. Les specs actives couvrent surtout Button, Color, Date, DatetimeLocal, Email. Il n’y a pas de couverture active pour Text, Hidden, File, Radio, Select, Textarea, Fieldset, Step, RichText, FormReference, ni pour les flows formidable-engine. Les exports commentés dans tests/cypress/support/fixtures/index.ts:9 confirment que la base de fixtures n’a pas suivi.
7.
Une partie notable de l’existant est déjà déclarée flaky ou désactivée. Checkbox est carrément désactivé dans tests/cypress/e2e/02-checkbox-input.cy.ts.disabled:1. Des blocs sont commentés pour Color, Date, DatetimeLocal. Ça indique que la suite n’est pas seulement incomplète, elle est aussi instable sur des cas simples.
8.
Les nouveaux domaines fonctionnels documentés n’ont pas de traduction en automation. Les scénarios existent pour logique conditionnelle, sécurité et permissions dans: tests/scenarios/conditional-logic.md tests/scenarios/security.md tests/scenarios/permissions.md Mais tests/cypress/e2e ne couvre rien de tout ça aujourd’hui.
9.
Le support Cypress masque potentiellement des régressions frontend. tests/cypress/support/e2e.js:27 retourne toujours false sur uncaught:exception. Avec l’arrivée de logique conditionnelle, Island hydration, CAPTCHA et Module Federation, ce réglage peut cacher de vrais défauts.
Ce qu’il faut mettre à jour
1.
Centraliser le setup de site et activer formidable-elements et formidable-engine ensemble.
2.
Réécrire createFormNode() pour créer les champs sous fields, et prévoir aussi la création d’actions quand le scénario teste la soumission.
3.
Séparer deux modes de test: preview pour le rendu jContent, public page ou POST direct pour les tests de soumission.
4.
Réaligner les page-objects avec le markup actuel: messages .fmdb-message*, nav multi-step, boutons next/previous, CAPTCHA, états de chargement.
5.
Corriger les helpers d’input pour émettre de vrais événements DOM.
6.
Réactiver ou remplacer les specs flaky avant d’élargir la suite.
7.
Compléter la couverture composant manquante: Text, Hidden, File, Radio, Select, Textarea, Fieldset, Step, RichText, FormReference.
8.
Ajouter une vraie couverture formidable-engine: submit pipeline, save2jcr, erreurs FMDB-*, auth, CAPTCHA, logique conditionnelle runtime.
9.
Ajouter les tests du dashboard résultats et des permissions, sinon toute la partie FormResults reste non testée.
10.
Réduire ou supprimer le uncaught:exception => false global, ou au minimum le limiter aux erreurs connues.
Priorité pratique
1.
Fixer le bootstrap modules.
2.
Fixer la structure JCR des fixtures (fields).
3.
Introduire un helper de visite hors preview pour les soumissions.
4.
Réaligner les page-objects.
5.
Ensuite seulement étendre la couverture fonctionnelle.
