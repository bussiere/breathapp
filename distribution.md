# Distribution

Ce projet se distribue en executable autonome avec `jpackage`. Chaque binaire contient l'application Java, ses dependances et un runtime Java dedie: l'utilisateur final n'a pas besoin d'installer Java.

Les scripts generent aussi une archive `.zip` prete a distribuer pour chaque systeme. Chaque build recoit une version incrementale globale, partagee par Windows, macOS et Linux.

## Pre-requis communs

- Python 3 en 64 bits.
- Un JDK 21 complet en 64 bits, pas seulement un JRE.
- `JAVA_HOME` doit pointer vers ce JDK 21.
- Les commandes `java` et `jpackage` utilisees par les scripts doivent venir du meme JDK 21.
- Le wrapper Gradle du projet: `gradlew` sous macOS/Linux, `gradlew.bat` sous Windows.

Java 21 est obligatoire pour toute la chaine: compilation Gradle, tests, `jpackage`, runtime embarque dans le zip. Les scripts refusent de packager si `java` ou `jpackage` ne sont pas en version 21.

Verification:

```bash
export JAVA_HOME=/chemin/vers/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
java -version
jpackage --version
python --version
```

`java -version` et `jpackage --version` doivent afficher Java 21. Un runtime 17 ou 22 ne doit pas etre utilise pour produire les zips de distribution.

Pour configurer la machine locale avec Ansible:

```bash
ansible-playbook ansible/setup-java21-sdkman.yml
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

Ce playbook installe SDKMAN si besoin, installe un JDK Temurin 21, puis le definit comme Java par defaut. Pour Windows, utilisez `ansible/setup-java21-windows.yml` depuis un controleur Ansible/WinRM, ou suivez l'equivalent Chocolatey documente dans `ansible/README.md`.

`jpackage` ne produit pas correctement des images applicatives pour un autre systeme que celui ou il est lance. Pour generer les trois distributions, lancez donc chaque script sur l'OS cible, ou sur trois runners/VM CI separes.

Avant de creer le zip, chaque script Python execute `:app:clean`, `:app:test`, `:app:installDist`, verifie que le jar embarque contient `test_sprite/chips.png`, refuse toute reference `chunli`, incremente la version de build, controle que le runtime embarque est Java 21, puis lance un smoke test `--export-demo` avec le binaire genere.

Les scripts de lancement recommandes sont:

- `scripts/build_linux_standalone.sh` pour Linux;
- `scripts/build_macosx_standalone.sh` pour macOS;
- `scripts/build_windows_standalone.cmd` pour Windows.

Les lanceurs Linux/macOS sourcent SDKMAN si disponible, puis appellent les scripts Python de packaging. Le lanceur Windows ajoute `%JAVA_HOME%\bin` au `PATH` si `JAVA_HOME` est defini, puis appelle le script Python Windows.

## Version des builds

La version est centralisee dans `build-version.properties` a la racine du projet:

```properties
baseVersion=1.0
buildNumber=0
```

Apres les verifications rapides (`:app:clean`, `:app:test`, `:app:installDist`, controle du jar), et juste avant `jpackage`, le script incremente `buildNumber` puis utilise la version `baseVersion.buildNumber` pour `jpackage --app-version`, pour le fichier `VERSION.txt` inclus dans l'application, et pour le nom du zip. Si `jpackage` ou le smoke test echoue ensuite, ce numero peut donc etre consomme sans zip final. Avec les valeurs initiales ci-dessus, le prochain build produit la version `1.0.1`. Le compteur est commun aux trois OS: si Linux produit `1.0.1`, le build Windows suivant produira `1.0.2`, puis macOS `1.0.3`, etc.

Pour changer de version mineure ou majeure, modifiez `baseVersion` manuellement et remettez `buildNumber=0` si vous voulez repartir de `.1`.

## Windows 64 bits

Depuis PowerShell ou `cmd.exe`, a la racine du projet:

```cmd
scripts\build_windows_standalone.cmd
```

Sortie:

```text
dist\windows-64\Breath\Breath.exe
dist\windows-64\Breath-windows-64-1.0.N.zip
```

Distribuez `dist\windows-64\Breath-windows-64-1.0.N.zip`. L'archive contient le dossier complet `Breath`, pas seulement le fichier `.exe`, car le runtime integre et les bibliotheques sont dans ce dossier. Le script genere une application GUI sans console Windows separee.

## macOS 64 bits

Depuis un terminal macOS, a la racine du projet:

```bash
scripts/build_macosx_standalone.sh
```

Sortie:

```text
dist/macos-64/Breath.app
dist/macos-64/Breath-macos-64-1.0.N.zip
```

Distribuez `dist/macos-64/Breath-macos-64-1.0.N.zip`. L'archive contient `Breath.app`. Le script cree une application non signee; selon la configuration de Gatekeeper, l'utilisateur peut devoir l'autoriser manuellement. Pour une distribution publique, signez et notarisez l'application avec un compte Apple Developer.

## Linux 64 bits

Depuis un terminal Linux, a la racine du projet:

```bash
scripts/build_linux_standalone.sh
```

Sortie:

```text
dist/linux-64/Breath/bin/Breath
dist/linux-64/Breath-linux-64-1.0.N.zip
```

Distribuez `dist/linux-64/Breath-linux-64-1.0.N.zip`. L'archive contient le dossier complet `Breath`, pas seulement le lanceur dans `bin/`.

## Upload GitHub des zips

Le depot GitHub officiel est documente dans `README.md` et doit contenir l'URL `https://github.com/bussiere/breathapp`. Le script d'upload lit cette URL dans `README.md`, en deduit le proprietaire et le nom du depot, puis demande le nom GitHub pour confirmation et le token en saisie masquee. Pour un upload non interactif, il lit aussi `github_name`/`GITHUB_NAME` et `github_token`/`GITHUB_TOKEN` depuis l'environnement.

Le token doit avoir le droit de creer ou modifier des GitHub Releases sur ce depot. Apres avoir genere les zips dans `dist/*/*.zip`, lancez:

```bash
scripts/upload_binary_github.py
```

Le script cree la release si le tag n'existe pas encore, ou reutilise la release existante. Il upload uniquement les zips correspondant a la version courante de `build-version.properties`; si un asset zip du meme nom existe deja sur la release, il est remplace avant upload.

## Release GitHub multi-OS

Le compteur de version est global et s'incremente a chaque build, quel que soit l'OS. Un cycle de release construit sur trois machines peut donc produire par exemple:

```text
Breath-linux-64-1.0.4.zip
Breath-windows-64-1.0.5.zip
Breath-macos-64-1.0.6.zip
```

Le script d'upload filtre volontairement sur la version courante pour eviter d'envoyer de vieux zips restes dans `dist/`. Pour une release publique qui regroupe les trois OS sous un meme tag, lancez l'upload juste apres chaque build sur la machine qui vient de produire son zip, et saisissez le meme tag de release a chaque fois, par exemple `v1.0-release-2026-07-25`. La release GitHub contiendra alors les trois assets, chacun avec son numero de build propre.

Si vous preferez un tag strictement egal au numero de version (`v1.0.N`), publiez chaque zip dans la release correspondant a son propre build.

## Nettoyage et regeneration

Chaque script supprime uniquement son dossier de sortie cible avant de reconstruire:

- `dist/windows-64`
- `dist/macos-64`
- `dist/linux-64`

Le build Gradle est aussi nettoye via `:app:clean` avant la creation de l'image applicative.
