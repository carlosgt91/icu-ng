# ICU NG — Fase 0: esqueleto de plugin — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tener un plugin ATAK (`com.carlosgt.atak.icung.plugin`) que carga dentro
de ATAK en el emulador `icu_ng_atak`, con un botón propio en el menú de
herramientas que abre un panel vacío — sin cámara, sin streaming, sin CoT
todavía. Ver [2026-09-17-icu-ng-design.md](../specs/2026-09-17-icu-ng-design.md)
(Fase 1 de esa spec) para el contexto completo.

**Architecture:** Renombrar la plantilla oficial de la SDK
(`samples/plugintemplate`) en el sitio, en vez de escribir un `IPlugin` desde
cero — es la única forma que tanto el build system como el pipeline de firma
esperan (ver `WORKFLOW.md` de `tools/atak-plugin-dev`). El panel vacío es el
`main_layout.xml` que ya trae la plantilla (un `TextView` con el nombre del
plugin), inflado con `PluginLayoutInflater`.

**Tech Stack:** Java 17, Android Gradle Plugin (vía el plugin `atak-takdev-plugin`),
Gradle 8.14.3 (wrapper de la plantilla), `gov.tak.api.plugin.IPlugin` (patrón
ATAK 5.7+), entorno `tools/atak-plugin-dev` (contenedor Docker).

## Global Constraints

- Paquete base: `com.carlosgt.atak.icung`. El paquete Java del código fuente
  del plugin, siguiendo el patrón de la propia plantilla (`<base>.plugin`),
  es `com.carlosgt.atak.icung.plugin`.
- Nombre visible en ATAK (`app_name`): **"ICU NG"**.
- Patrón de plugin: `IPlugin` nuevo estilo (`gov.tak.api.plugin.IPlugin`),
  `assets/plugin.xml` — el patrón de plantilla desde ATAK 5.7+. No usar el
  patrón antiguo de `Tool`/`DropDownReceiver`.
- Build **solo** a través de `tools/atak-plugin-dev` (contenedor). No instalar
  toolchain de Android a mano en el host más allá de lo que ya exige el
  entorno (emulador — ver más abajo).
- El SDK de ATAK (`D:\Desarollo\ATAK-CIV-5.8.0.5-SDK`, montado en
  `/opt/atak-sdk` dentro del contenedor) tiene licencia de tak.gov — nunca se
  commitea nada copiado literalmente de ahí. `scan` corre antes de cada
  commit.
- Dispositivo de prueba: emulador `icu_ng_atak` (Android 14, `google_apis`,
  x86_64) — ya creado y arrancado, con el `atak.apk` de la SDK (`5.8.0.5`)
  instalado. Nunca usar `aosp_atd`.
- Todos los comandos de host en este plan están en PowerShell (es el shell
  con el que se levantó el contenedor y el emulador en esta máquina; no hay
  distro WSL2 de usuario disponible).
- Esta fase **no** toca cámara, RootEncoder, streaming RTSP ni CoT — eso es
  Fase 1 en adelante, en un plan aparte.

---

## File Structure

Todo dentro de `icu-replacement/` (ya existe con `CLAUDE.md` y
`docs/superpowers/`, que se preservan):

```
icu-replacement/
├── CLAUDE.md                          (ya existe, sin tocar)
├── docs/                              (ya existe, sin tocar)
├── build.gradle                       (nuevo — copiado de la plantilla, sin cambios)
├── settings.gradle                    (nuevo — copiado + rootProject.name añadido)
├── gradle.properties                  (nuevo — copiado de la plantilla, sin cambios)
├── gradlew / gradlew.bat              (nuevo — copiado de la plantilla, sin cambios)
├── gradle/
│   ├── typst.gradle                   (nuevo — copiado, sin cambios; nunca se activa fuera de ATAK_CI=1)
│   ├── versions.gradle                (nuevo — copiado, sin cambios)
│   └── wrapper/                       (nuevo — copiado, sin cambios)
├── local.properties                   (nuevo — creado a mano, NUNCA committeado, ver .gitignore raíz)
└── app/
    ├── build.gradle                   (nuevo — copiado + namespace actualizado)
    ├── proguard-gradle.txt            (nuevo — copiado, sin cambios)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml    (nuevo — copiado, sin cambios; ver por qué abajo)
        │   ├── assets/plugin.xml      (nuevo — copiado + impl= actualizado)
        │   ├── java/com/carlosgt/atak/icung/plugin/
        │   │   └── IcuNgPlugin.java   (nuevo — renombrado desde PluginTemplate.java)
        │   └── res/
        │       ├── drawable/          (nuevo — copiado, sin cambios)
        │       ├── layout/main_layout.xml  (nuevo — copiado, sin cambios: ya es el "panel vacío")
        │       └── values/
        │           ├── strings.xml    (nuevo — copiado + app_name/app_desc actualizados)
        │           ├── colors.xml, dimen.xml, styles.xml  (nuevo — copiados, sin cambios)
        ├── gov/res/values/strings.xml (nuevo — copiado, sin cambios; flavor "gov" no se usa en Fase 0)
        └── test/java/com/atakmap/android/test/
            └── ExampleTest.java       (nuevo — copiado, sin cambios; verifica que testCivDebugUnitTest funciona)
```

**Qué se elimina de la plantilla copiada, y por qué** (parte de la tarea de
renombrado, no limpieza aparte):
- `README.md` — plantilla de placeholders (`(Plugin Name)`, `(General
  Description)`...) sin contenido real. La convención del monorepo
  (`../CLAUDE.md`) ya cubre el "cómo técnico"; un README vacío al lado solo
  confunde.
- `docs/user_manual/` (incluidas las fuentes `.otf`) — plantilla de manual de
  usuario en Typst, ~1MB de fuentes sin usar. Solo se activa si
  `ATAK_CI=1` o se invoca `runTypst` a mano (ver `gradle/typst.gradle:124`);
  no aplica a una fase que todavía no tiene funcionalidad que documentar.
  `gradle/typst.gradle` se deja intacto — no hace falta tocarlo, no rompe
  nada con la carpeta ausente (avisa y no hace nada si no encuentra `.typ`).
- `app/src/main/java/.../PluginNativeLoader.java` — clase sin usar en ningún
  sitio de la plantilla; si se moviera junto al resto del paquete habría que
  actualizar su `package` igualmente sin que aporte nada en esta fase (el
  plugin no usa librerías nativas). Se recupera de la SDK si algún día hace
  falta cargar una librería nativa.

## Task 1: Scaffold del plugin y build local

**Files:**
- Create: todo lo listado en "File Structure" bajo `icu-replacement/` excepto
  `app/src/main/java/.../PluginNativeLoader.java`.
- Modify: `icu-replacement/app/build.gradle`, `icu-replacement/settings.gradle`,
  `icu-replacement/app/src/main/assets/plugin.xml`,
  `icu-replacement/app/src/main/res/values/strings.xml`.

**Interfaces:**
- Produces: clase `com.carlosgt.atak.icung.plugin.IcuNgPlugin` implementando
  `gov.tak.api.plugin.IPlugin`, con constructor
  `IcuNgPlugin(gov.tak.api.plugin.IServiceController serviceController)`. La
  Tarea 2 (y toda fase futura) la referencia por este nombre completo.

- [ ] **Step 1: Copiar la plantilla de la SDK dentro de `icu-replacement/`**

```powershell
Copy-Item -Recurse -Force `
    "D:\Desarollo\ATAK-CIV-5.8.0.5-SDK\samples\plugintemplate\*" `
    "D:\Documents\atak-plugins\icu-replacement\"
```

Esto copia el contenido de la plantilla (no la carpeta en sí) dentro de
`icu-replacement/`, preservando `CLAUDE.md` y `docs/` que ya existían.

- [ ] **Step 2: Verificar qué se copió, y eliminar lo que no se necesita en esta fase**

```powershell
Get-ChildItem "D:\Documents\atak-plugins\icu-replacement" -Recurse -Depth 1 | Select-Object Name
```

Expected: se ven `app/`, `build.gradle`, `settings.gradle`, `gradlew`,
`gradlew.bat`, `gradle/`, `gradle.properties`, `README.md`, `docs/` (mezclado
con el `docs/superpowers/` ya existente), `CLAUDE.md`.

```powershell
Remove-Item -Force "D:\Documents\atak-plugins\icu-replacement\README.md"
Remove-Item -Recurse -Force "D:\Documents\atak-plugins\icu-replacement\docs\user_manual"
Remove-Item -Force "D:\Documents\atak-plugins\icu-replacement\app\src\main\java\com\atakmap\android\plugintemplate\plugin\PluginNativeLoader.java"
```

Expected: `docs/` de `icu-replacement/` solo contiene ahora `superpowers/`
(comprobar con `Get-ChildItem "D:\Documents\atak-plugins\icu-replacement\docs"`).

- [ ] **Step 3: Mover y renombrar el paquete Java**

```powershell
New-Item -ItemType Directory -Force -Path "D:\Documents\atak-plugins\icu-replacement\app\src\main\java\com\carlosgt\atak\icung\plugin" | Out-Null
Move-Item `
    "D:\Documents\atak-plugins\icu-replacement\app\src\main\java\com\atakmap\android\plugintemplate\plugin\PluginTemplate.java" `
    "D:\Documents\atak-plugins\icu-replacement\app\src\main\java\com\carlosgt\atak\icung\plugin\IcuNgPlugin.java"
Remove-Item -Recurse -Force "D:\Documents\atak-plugins\icu-replacement\app\src\main\java\com\atakmap"
```

- [ ] **Step 4: Editar `IcuNgPlugin.java` — paquete y nombre de clase**

Reemplazar la línea 2 y la línea 18 (usar el editor; contenido exacto
esperado tras el cambio, resto del fichero **sin tocar**):

```java
package com.carlosgt.atak.icung.plugin;
```

```java
public class IcuNgPlugin implements IPlugin {
```

Y el constructor:

```java
    public IcuNgPlugin(IServiceController serviceController) {
```

- [ ] **Step 5: Actualizar `app/src/main/assets/plugin.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- if you change this file, make sure to tick the versionCode in AndroidManifest. -->
<plugin>

    <extension
        type="gov.tak.api.plugin.IPlugin"
        impl="com.carlosgt.atak.icung.plugin.IcuNgPlugin"
        singleton="true" />

</plugin>
```

- [ ] **Step 6: Actualizar `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- App -->
    <string name="app_name">ICU NG</string>
    <!-- description -->
    <string name="app_desc">Sustituto de TAK ICU: streaming de cámara con audio, anunciado en el mapa vía CoT.</string>
</resources>
```

- [ ] **Step 7: Actualizar `app/build.gradle` — namespace**

Cambiar únicamente la línea del `namespace` dentro del bloque `android {}`
(línea 106 de la plantilla original):

```groovy
    namespace 'com.carlosgt.atak.icung.plugin'
```

El resto de `app/build.gradle` se deja exactamente como venía de la
plantilla (flavors, signing, packaging — nada de eso cambia en esta fase).

- [ ] **Step 8: Añadir `rootProject.name` en `settings.gradle`**

Contenido completo esperado del fichero tras el cambio:

```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}

rootProject.name = 'icu-ng'

include ':app'
```

- [ ] **Step 9: Crear `local.properties` (no se commitea — ver `.gitignore` raíz)**

```powershell
Set-Content -Path "D:\Documents\atak-plugins\icu-replacement\local.properties" -Encoding utf8 -Value @"
sdk.dir=/opt/android-sdk
sdk.path=/opt/atak-sdk
takdev.plugin=/opt/atak-sdk/atak-gradle-takdev.jar
"@
```

- [ ] **Step 10: Compilar y correr los tests JVM dentro del contenedor**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew testCivDebugUnitTest"
```

Expected: `BUILD SUCCESSFUL`, con `ExampleTest > basicArithmetic PASSED` en
la salida (verifica que el rename no rompió el build, aunque el test en sí
no toca código nuestro todavía — no hay lógica propia que testear en esta
fase).

- [ ] **Step 11: `scan` antes de commitear**

```powershell
docker compose exec atak-dev scan icu-replacement
```

Expected: sale limpio (sin secretos, sin material de SDK colado, sin CVEs
bloqueantes). Si marca algo, resolverlo antes de seguir al Step 12 — no
commitear con un `scan` en rojo.

- [ ] **Step 12: Commit**

```powershell
Set-Location "D:\Documents\atak-plugins"
git add icu-replacement
git status --short
```

Revisar la salida de `git status --short`: deben aparecer solo ficheros
bajo `icu-replacement/app`, `icu-replacement/build.gradle`,
`icu-replacement/settings.gradle`, `icu-replacement/gradle.properties`,
`icu-replacement/gradle/`, `icu-replacement/gradlew`,
`icu-replacement/gradlew.bat` — **nunca** `local.properties`, `build/` ni
`.gradle/` (los excluye el `.gitignore` raíz).

```powershell
git commit -m "feat(icu-replacement): esqueleto de plugin IPlugin (ICU NG)

Plantilla de la SDK renombrada a com.carlosgt.atak.icung.plugin /
IcuNgPlugin. Sin camara ni streaming todavia - solo boton de menu y
panel vacio (Fase 0 de la spec de diseno).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

## Task 2: Deploy, carga en ATAK y verificación en el emulador

**Files:** ninguno (esta tarea no modifica código; verifica el resultado de
la Tarea 1 en el dispositivo).

**Interfaces:**
- Consumes: `com.carlosgt.atak.icung.plugin.IcuNgPlugin` (Tarea 1) — se
  espera que `deploy` lo compile, instale y lo deje listo para cargar.

- [ ] **Step 1: Build + install + stage para sideload**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev deploy icu-replacement
```

Expected: termina sin error, y el log menciona la instalación del APK y el
staging en `/sdcard/atak/support/apks/sideloaded/`.

- [ ] **Step 2: Sincronizar y cargar el plugin en ATAK (en el emulador)**

En la pantalla del emulador `icu_ng_atak` (con ATAK ya abierto — si no lo
está: `adb -s emulator-5554 shell am start -n com.atakmap.app.civ/com.atakmap.app.SplashActivity`):

1. Menú hamburguesa → **Tools** → **Plugins**.
2. Icono de sincronizar (arriba a la derecha) → **OK**.
3. Tocar la fila de **ICU NG**.
4. **Load** → **OK**.

Expected: el estado de la fila pasa de `Not loaded` a `Loaded`, y "ICU NG"
aparece como icono nuevo en el cajón de Tools.

- [ ] **Step 3: Verificar por log que no hay problema de firma y que cargó**

```powershell
docker compose exec atak-dev bash -lc 'adb logcat -d | rg "AtakPluginRegistry|PluginValidator"'
```

Expected: se ve `SDK skipping signature check[com.carlosgt.atak.icung...]`
(confirma que estamos en la build de desarrollador, no en la release — si
en su lugar aparece `signature mismatch`, el `atak.apk` de la SDK no está
instalado o se reinstaló la release por encima) y una línea `Loaded
com.carlosgt.atak.icung.plugin.IcuNgPlugin`.

- [ ] **Step 4: Confirmar visualmente el panel vacío**

Tocar el icono "ICU NG" en el cajón de Tools.

Expected: se abre un panel con el texto "ICU NG" (el `main_layout.xml` de
la plantilla, sin cambios — es el "panel vacío" de esta fase). Ninguna
petición de permisos de cámara/micrófono debe aparecer todavía — eso llega
en la Fase 1 del plan general.

- [ ] **Step 5: Confirmar que no es "Incompatible"**

Volver a **Tools → Plugins**, tocar la fila de ICU NG otra vez.

Expected: el detalle dice **"is loaded and current"**. Si en cambio dice
**"Incompatible"**, es el problema de firma descrito en `SIGNING.md` — no
seguir a fases posteriores hasta resolverlo (normalmente significa que se
instaló el ATAK de release en vez del `atak.apk` de la SDK; repetir la
instalación del Step 5 de `doctor`).

Sin commit en esta tarea — no se ha modificado ningún fichero, solo se ha
verificado el resultado de la Tarea 1 en el dispositivo.

---

## Self-Review

- **Cobertura de la Fase 0 de la spec** ("esqueleto: `IPlugin` renombrado,
  botón en el menú, `Pane` vacío. Hecho = carga en ATAK vía
  deploy/sync/Load, aparece en el drawer de Tools"): cubierto por Task 1
  (renombrado) + Task 2 (deploy/sync/Load/verificación) completos.
- **Placeholders:** ninguno — cada step tiene contenido exacto (rutas,
  código, comandos y salida esperada reales, comprobados contra la SDK
  5.8.0.5 y la plantilla tal cual está en disco).
- **Consistencia de nombres:** `com.carlosgt.atak.icung.plugin.IcuNgPlugin`
  usado de forma idéntica en `plugin.xml`, `build.gradle` (namespace),
  Step 4 de la Tarea 1, y en la verificación por log de la Tarea 2.
- **Fuera de alcance, deliberadamente:** cámara, RootEncoder, RTSP, CoT,
  `IVideoConnectionManager`, gating por GPS — todo eso es Fase 1 en
  adelante (plan aparte, una vez este esqueleto esté cargando limpio).
