# ICU NG — Fase 1: cámara + RootEncoder + preview local — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El panel de ICU NG (hasta ahora vacío, Fase 0) muestra el preview en
vivo de la cámara trasera del dispositivo, capturado vía RootEncoder. Sin
streaming, sin servidor, sin CoT todavía — solo captura + render local. Ver
[2026-09-17-icu-ng-design.md](../specs/2026-09-17-icu-ng-design.md) (fase
"Cámara + RootEncoder + preview local" del diseño general).

**Architecture:** RootEncoder se usa solo por su capa de captura de cámara
(`Camera2Base`/`RtspCamera2`) enlazada a una `OpenGlView` embebida en el
layout del panel — sin tocar la parte de streaming de la librería en esta
fase (`startStream`/`prepareVideo`/`prepareAudio` quedan para la Fase 2). El
plugin ATAK no pide permiso de cámara por su cuenta: el código del plugin se
ejecuta en el mismo proceso que ATAK (cargado por su classloader), y ATAK ya
tiene `CAMERA`/`RECORD_AUDIO` concedidos — confirmado con
`dumpsys package com.atakmap.app.civ` en el emulador (`granted=true` para
ambos). Esto está documentado en el propio SDK: el Javadoc de
`PluginPermissionActivity` (muestra de ejemplo `helloworld`) dice
textualmente que el código del plugin "inherits privileges from the core
ATAK application" — a diferencia de una Activity/Service propia del plugin,
que se ejecutaría con una identidad separada y sí necesitaría pedir permisos
ella misma.

**Tech Stack:** [RootEncoder](https://github.com/pedroSG94/RootEncoder)
(pedroSG94, Apache-2.0) v2.8.1 vía JitPack, módulo `library`
(`com.pedro.library.*`, con `com.pedro.encoder.*` y `com.pedro.common.*`
como dependencias transitivas).

## Global Constraints

- Todo lo de `icu_ng_atak` (emulador) y `tools/atak-plugin-dev` (contenedor)
  de la Fase 0 aplica igual aquí — ver
  [2026-09-17-icu-ng-phase0-skeleton.md](2026-09-17-icu-ng-phase0-skeleton.md)
  Global Constraints.
- **Docker Desktop y el emulador no sobreviven a un reinicio de la máquina ni
  a estar mucho rato inactivos** (visto repetidamente en la Fase 0) —
  primer paso de cualquier sesión de trabajo: comprobar `docker compose exec
  atak-dev doctor` y, si falla, levantar Docker Desktop y el emulador antes
  de nada.
- Dependencia nueva: `com.github.pedroSG94.RootEncoder:library:2.8.1`. El
  repositorio `https://jitpack.io` ya está declarado en
  `settings.gradle` (añadido en la Fase 0) — no hace falta tocarlo.
- Cámara objetivo: **trasera únicamente**
  (`CameraHelper.Facing.BACK`) — el AVD `icu_ng_atak` tiene
  `hw.camera.front=none` en su `config.ini`, así que pedir la frontal
  fallaría en este dispositivo de pruebas.
- Fuera de alcance de esta fase (quedan para después, según el diseño
  general): `prepareVideo`/`prepareAudio`, `startStream`/`stopStream`,
  cambio de cámara, foto durante grabación, cualquier UI de configuración de
  servidor.

## File Structure

```
icu-replacement/
└── app/
    ├── build.gradle                                    (modificar: dependencia RootEncoder)
    └── src/main/
        ├── AndroidManifest.xml                         (modificar: permiso CAMERA)
        ├── res/layout/main_layout.xml                  (modificar: OpenGlView en vez del TextView)
        └── java/com/carlosgt/atak/icung/plugin/
            └── IcuNgPlugin.java                         (modificar: RtspCamera2 + ConnectChecker)
```

Ningún fichero nuevo — todo son cambios sobre lo que dejó la Fase 0.

## Task 1: Dependencia, permiso y layout del preview

**Files:**
- Modify: `icu-replacement/app/build.gradle`
- Modify: `icu-replacement/app/src/main/AndroidManifest.xml`
- Modify: `icu-replacement/app/src/main/res/layout/main_layout.xml`

**Interfaces:**
- Produces: `@+id/preview_view` (una `com.pedro.library.view.OpenGlView` en
  `main_layout.xml`) — la Tarea 2 hace `findViewById(R.id.preview_view)`
  sobre este id.

- [ ] **Step 1: Añadir la dependencia de RootEncoder**

En `icu-replacement/app/build.gradle`, dentro del bloque `dependencies {}`
(el mismo donde la Fase 0 añadió `testImplementation junit`):

```groovy
dependencies {
    implementation fileTree(dir: 'libs', include: '*.jar')
    testImplementation 'junit:junit:4.13.2'
    implementation 'com.github.pedroSG94.RootEncoder:library:2.8.1'
}
```

- [ ] **Step 2: Añadir el permiso de cámara al manifest**

En `icu-replacement/app/src/main/AndroidManifest.xml`, añadir la línea de
`uses-permission` junto a la raíz del `<manifest>` (antes de
`<application>`):

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="GoogleAppIndexingWarning">

    <uses-permission android:name="android.permission.CAMERA" />

    <application
```

(El resto del fichero, desde `<application` en adelante, se queda exactamente
igual que en la Fase 0 — no tocar el `activity` de
`com.atakmap.app.component`.)

- [ ] **Step 3: Sustituir el contenido del panel por el preview de cámara**

Reemplazar todo el contenido de
`icu-replacement/app/src/main/res/layout/main_layout.xml` por:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.pedro.library.view.OpenGlView
        android:id="@+id/preview_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```

Se deja como `FrameLayout` (en vez de volver a un `ScrollView`) a propósito:
las fases 2 y 3 del diseño añaden controles (start/stop, estado del
servidor) superpuestos sobre el preview, no debajo en scroll.

- [ ] **Step 4: Verificar que compila (sin desplegar todavía)**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`. Si falla resolviendo
`com.github.pedroSG94.RootEncoder`, comprobar que `jitpack.io` sigue en
`settings.gradle` (lo añadió la Fase 0) antes de mirar nada más.

- [ ] **Step 5: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking` (igual que al final de la Fase 0 — recordar
limpiar `build/` antes de escanear, si no sale el mismo falso positivo de
`debuggable` que en la Fase 0).

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/build.gradle icu-replacement/app/src/main/AndroidManifest.xml icu-replacement/app/src/main/res/layout/main_layout.xml
git status --short
```

Revisar que solo aparecen esos 3 ficheros modificados (`M`), nada de
`build/`, `.gradle/`, `.takdev/` ni `local.properties`.

```bash
git commit -m "$(cat <<'EOF'
feat(icu-replacement): dependencia RootEncoder, permiso de camara y layout del preview

Prepara el panel para mostrar el preview de camara (Fase 1): anade
RootEncoder via JitPack, el permiso CAMERA en el manifest, y cambia
main_layout.xml de un TextView a una OpenGlView a pantalla completa
dentro de un FrameLayout (para dejar sitio a controles en fases
posteriores). Sin logica de camara todavia - eso es la Tarea 2.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 2: Enganchar RtspCamera2 al ciclo de vida del panel

**Files:**
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Consumes: `@+id/preview_view` (Tarea 1, `main_layout.xml`).
- Produces: nada nuevo expuesto fuera de la clase — el campo `camera`
  (`RtspCamera2`) es privado a `IcuNgPlugin`; fases posteriores (2, 3) lo
  amplían dentro del mismo fichero, no lo consumen desde fuera.

- [ ] **Step 1: Añadir los imports de RootEncoder**

En `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`,
junto a los imports ya existentes de `gov.tak.api.*`:

```java
import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.library.rtsp.RtspCamera2;
import com.pedro.library.view.OpenGlView;
```

- [ ] **Step 2: Implementar `ConnectChecker` en la clase del plugin**

Cambiar la declaración de la clase (línea con `public class IcuNgPlugin`):

```java
public class IcuNgPlugin implements IPlugin, ConnectChecker {
```

Añadir los 6 métodos de `ConnectChecker` — en esta fase no hacen nada
todavía (no hay `startStream` que dispare estos callbacks), pero la clase
tiene que implementarlos para compilar. Añadirlos al final de la clase,
antes de la llave de cierre:

```java
    @Override
    public void onConnectionStarted(String url) {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onConnectionSuccess() {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onConnectionFailed(String reason) {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onDisconnect() {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onAuthError() {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onAuthSuccess() {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }
```

- [ ] **Step 3: Añadir el campo `camera` junto a los campos existentes**

```java
    IServiceController serviceController;
    Context pluginContext;
    IHostUIService uiService;
    ToolbarItem toolbarItem;
    Pane templatePane;
    RtspCamera2 camera;
```

- [ ] **Step 4: Arrancar el preview dentro de `showPane()`**

Reemplazar el método `showPane()` completo por:

```java
    private void showPane() {
        // instantiate the plugin view if necessary
        if(templatePane == null) {
            // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
            // In this case, using it is not necessary - but I am putting it here to remind
            // developers to look at this Inflator

            View paneView = PluginLayoutInflater.inflate(pluginContext,
                    R.layout.main_layout, null);

            OpenGlView previewView = paneView.findViewById(R.id.preview_view);
            camera = new RtspCamera2(previewView, this);

            templatePane = new PaneBuilder(paneView)
                    // relative location is set to default; pane will switch location dependent on
                    // current orientation of device screen
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    // pane will take up 50% of screen width in landscape mode
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    // pane will take up 50% of screen height in portrait mode
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }

        // if the plugin pane is not visible, show it!
        if(!uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, null);
        }

        if (camera != null && !camera.isOnPreview()) {
            camera.startPreview(CameraHelper.Facing.BACK);
        }
    }
```

Esto añade el `import android.view.View;` que falta — confirmarlo junto a
los demás imports de `android.*` (`android.content.Context` ya estaba desde
la Fase 0):

```java
import android.view.View;
```

- [ ] **Step 5: Parar el preview en `onStop()`**

Reemplazar el método `onStop()` completo por:

```java
    @Override
    public void onStop() {
        // the plugin is stopping, remove the button from the toolbar
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);

        if (camera != null && camera.isOnPreview()) {
            camera.stopPreview();
        }
    }
```

*(Simplificación conocida de esta fase: el preview solo se para cuando el
plugin entero se detiene, no cuando el usuario simplemente cierra el panel
dejando el plugin activo — o sea, la cámara puede quedarse abierta de fondo
mientras ATAK sigue corriendo con el plugin cargado. Vivible para una fase
de desarrollo/verificación; revisar si hace falta un cierre más fino
—enganchado a la visibilidad del `Pane`— antes de dar por buena esta
fase para producción real.)*

- [ ] **Step 6: Compilar**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`. Errores esperables si algo del Step 2 o 4 se
copió mal: `IcuNgPlugin is not abstract and does not override abstract
method ... in ConnectChecker` (falta alguno de los 6 métodos) o `cannot
find symbol: variable View` (falta el import del Step 4).

- [ ] **Step 7: `scan`, y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
feat(icu-replacement): capturar y mostrar el preview de la camara trasera

RtspCamera2 (RootEncoder) enlazado a la OpenGlView del panel: arranca
en startPreview(BACK) al abrir el panel, para en onStop(). Solo
captura/renderiza en local - streaming, CoT y control de servidor
llegan en fases posteriores.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 3: Deploy y verificación visual en el emulador

**Files:** ninguno (verifica en el dispositivo lo que dejaron las Tareas 1 y 2).

**Interfaces:**
- Consumes: APK de `icu-replacement` con `IcuNgPlugin` cargando la cámara
  (Tareas 1-2).

- [ ] **Step 1: Confirmar que el entorno sigue sano**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev doctor
```

Si falla en cualquier punto, seguir la misma secuencia de arreglo que en la
Fase 0 (Docker Desktop, `adb -a -P 5037 server nodaemon` en el host,
arrancar `icu_ng_atak`) antes de continuar.

- [ ] **Step 2: Build + install + stage**

```powershell
docker compose exec atak-dev deploy icu-replacement
```

Expected: `BUILD SUCCESSFUL`, `Performing Streamed Install` → `Success`,
`staged ATAK-Plugin-icu-ng-...apk`.

- [ ] **Step 3: Sync + Load en ATAK (igual que la Fase 0)**

Si ATAK ya tenía la versión de la Fase 0 cargada, hace falta repetir
sync+Load para que recoja el nuevo APK: hamburguesa (Tools) → vista de
lista → **Plugins** → **Sync Now** → OK → fila **ICU NG** → **Load** → OK.

Expected: la fila pasa a `Loaded` / `Current`, igual que en la Fase 0.

- [ ] **Step 4: Verificación visual — el preview real, no un placeholder**

Abrir el panel de ICU NG (Tools → lista → ICU NG).

Expected: en vez del texto "ICU NG" de la Fase 0, se ve la imagen de la
cámara trasera **emulada** del AVD (el patrón sintético de colores/franjas
que genera `hw.camera.back=emulated` — no una cámara real, pero sí un feed
vivo, no una imagen estática). Confirma que `RtspCamera2` abrió la cámara y
`OpenGlView` está renderizando frames.

- [ ] **Step 5: Verificación por log — confirmar la hipótesis de permisos**

```powershell
docker compose exec atak-dev bash -lc 'adb logcat -d | grep -i "icung\|SecurityException\|CameraAccessException"' 2>&1 | Select-Object -Last 40
```

Expected: **ninguna** línea con `SecurityException` ni
`CameraAccessException` referida a nuestro paquete. Si aparece una
`SecurityException: Permission Denial` para `com.carlosgt.atak.icung`, la
hipótesis de "el plugin hereda los permisos de ATAK" (ver Architecture,
arriba) no se sostiene en este build/versión de ATAK, y hace falta una
`PluginPermissionActivity` propia (patrón de la muestra `helloworld` del
SDK: `samples/helloworld/app/src/main/java/com/atakmap/android/helloworld/PluginPermissionActivity.java`)
antes de seguir a la Fase 2 — no asumir, comprobarlo aquí.

Sin commit en esta tarea — solo verificación en el dispositivo de lo que
dejaron las Tareas 1 y 2.

---

## Self-Review

- **Cobertura de la Fase 1 del diseño** ("Cámara + RootEncoder + preview
  local — captura y muestra preview en el Pane, sin emitir aún. Hecho = ver
  la cámara del emulador en el panel del plugin"): cubierto — Task 1 prepara
  dependencia/permiso/layout, Task 2 conecta `RtspCamera2` al ciclo de vida
  del panel, Task 3 verifica visualmente y por log que el preview
  realmente renderiza frames de cámara, no solo que compila.
- **Placeholders:** ninguno — todas las clases, paquetes y firmas de método
  de RootEncoder citadas (`RtspCamera2`, `Camera2Base`, `OpenGlView`,
  `ConnectChecker`, `CameraHelper.Facing`) se verificaron contra el código
  fuente real del repo `pedroSG94/RootEncoder` en la rama `master`, no de
  memoria.
- **Consistencia de nombres:** `camera` (`RtspCamera2`), `preview_view`
  (id de la `OpenGlView`) y `CameraHelper.Facing.BACK` se usan igual en
  Task 1 (layout) y Task 2 (Java) — un solo punto de verdad para cada
  nombre.
- **Riesgo identificado y no asumido a ciegas:** la hipótesis de que el
  plugin no necesita pedir permiso de cámara por sí mismo (por ejecutarse
  en el proceso de ATAK) está justificada con evidencia (Javadoc de la
  propia SDK + `dumpsys package` mostrando `granted=true`), pero Task 3
  Step 5 la verifica en el dispositivo real en vez de darla por sentada — y
  deja escrito el plan B exacto (`PluginPermissionActivity`) si falla.
- **Fuera de alcance, deliberadamente:** `prepareVideo`/`prepareAudio`,
  `startStream`, control de servidor, cambio de cámara, cierre del preview
  al ocultar el panel (solo se para al detener el plugin entero) — todo eso
  es Fase 2 en adelante o un refinamiento a decidir más tarde.
