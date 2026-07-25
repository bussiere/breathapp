# Java 21 Local Setup

The project must be built, tested, packaged, and distributed with Java 21. The packaging scripts reject any other major version for both `java` and `jpackage`.

## Linux/macOS with SDKMAN

```bash
ansible-playbook ansible/setup-java21-sdkman.yml
source "$HOME/.sdkman/bin/sdkman-init.sh"
java -version
jpackage --version
scripts/build_linux_standalone.sh
```

The default SDKMAN candidate is `21.0.4-tem`, matching the local project machine. Override it when another Temurin 21 candidate is preferred or already installed:

```bash
ansible-playbook ansible/setup-java21-sdkman.yml -e java_sdkman_version=21.0.11-tem
```

## Windows with Ansible

Run this from an Ansible controller that can reach the Windows host over WinRM:

```bash
ansible-galaxy collection install ansible.windows chocolatey.chocolatey
ansible-playbook -i inventory.ini ansible/setup-java21-windows.yml
```

For a single Windows developer machine without Ansible/WinRM, install Temurin JDK 21 with Chocolatey and make sure `JAVA_HOME` and `PATH` point to that JDK before running `scripts\build_windows_standalone.cmd`.

```powershell
choco install temurin21 -y
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
jpackage --version
scripts\build_windows_standalone.cmd
```
