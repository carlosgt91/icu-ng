# ICU NG — Pulido de UI del panel — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dos cosas, sin tocar arquitectura ni ciclo de vida: (1) los mensajes
de estado dejan de mostrar texto crudo/en inglés de `ConnectChecker`
(`reason` de `onConnectionFailed`) y pasan a ser neutros y en español, con
el detalle técnico solo en `Log` para depuración; (2) un repaso visual del
panel ahora que tiene selector de protocolo + campos de cifrado + URL —
espaciado, protocolos más legibles en el espacio estrecho del panel (50%
de pantalla), y un aviso persistente para el límite de 10-79 caracteres del
passphrase SRT que hoy desaparece en cuanto se empieza a escribir.

**Architecture:** Solo `IcuNgPlugin.java` (los 6 métodos de `ConnectChecker`
+ los 3 sitios de `toggleStream()`/`onProtocolSelected()` que ya construían
mensajes de estado) y recursos (`strings.xml`, `colors.xml` ya existente
—creado por la plantilla del SDK, sin usar hasta ahora—, `arrays.xml`,
`main_layout.xml`). Cero cambios en la construcción/ciclo de vida de
`camera`, `showPane()`/`tearDownPane()`, o el envío CoT — nada de eso se
toca.

## Global Constraints

- Mismo entorno de siempre (`doctor` primero, emulador `icu_ng_atak`).
- `pluginContext.getResources().getColor(int)` (un solo argumento) está
  deprecado — usar `androidx.core.content.ContextCompat.getColor(context, id)`
  en su lugar (ya disponible: `androidx.core` es dependencia transitiva fijada
  en `build.gradle`).

## File Structure

```
icu-replacement/app/src/main/
├── java/com/carlosgt/atak/icung/plugin/
│   └── IcuNgPlugin.java   (modificar: estado neutro + color por estado)
└── res/
    ├── layout/main_layout.xml   (modificar: espaciado, caption passphrase)
    └── values/
        ├── strings.xml   (modificar: strings de estado nuevos, quitar status_error_format)
        ├── colors.xml    (modificar: colores de estado — ya existe, vacío de uso)
        └── arrays.xml    (modificar: entradas de protocolo más cortas)
```

## Task 1: Mensajes de estado neutros + color por estado

**Files:**
- Modify: `icu-replacement/app/src/main/res/values/strings.xml`
- Modify: `icu-replacement/app/src/main/res/values/colors.xml`
- Modify: `icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java`

**Interfaces:**
- Produces: `setStatusIdle()`, `setStatusConnecting()`, `setStatusStreaming()`,
  `setStatusDisconnected()`, `setStatusError(String)` — sustituyen todas las
  llamadas directas a `setStatus(String)` existentes (que se queda como
  helper interno con color, no se llama desde fuera).

- [ ] **Step 1: Strings de estado — reemplazar `status_error_format`**

En `strings.xml`, reemplazar el bloque de estado actual:

```xml
    <string name="status_idle">Detenido</string>
    <string name="status_connecting">Conectando…</string>
    <string name="status_streaming">Emitiendo</string>
    <string name="status_disconnected">Desconectado</string>
    <string name="status_error_format">Error: %1$s</string>
```

por:

```xml
    <string name="status_idle">Detenido</string>
    <string name="status_connecting">Conectando…</string>
    <string name="status_streaming">Emitiendo</string>
    <string name="status_disconnected">Desconectado</string>
    <string name="status_error_connection">Error de conexión</string>
    <string name="status_error_auth">Error de autenticación</string>
    <string name="status_error_scheme_format">Error: la URL debe empezar por %1$s://</string>
    <string name="status_error_srt_passphrase_length">Error: el passphrase SRT debe tener entre 10 y 79 caracteres</string>
    <string name="status_error_change_protocol_while_streaming">Error: detén la emisión antes de cambiar a/desde SRT</string>
```

Cada mensaje de error queda completo en su propio recurso (nada de
concatenar texto en Java) — los dos primeros son los que antes mostraban
texto crudo de la librería; los tres siguientes son validaciones nuestras,
ya neutras, que antes se construían con `+` en `toggleStream()`/
`onProtocolSelected()`.

- [ ] **Step 2: Colores de estado**

En `colors.xml` (ya existe, con `white`/`darker_gray`/`heading_yellow` sin
usar todavía — añadir sin tocar esas tres):

```xml
    <color name="status_neutral">@color/white</color>
    <color name="status_connecting">#FFC107</color>
    <color name="status_streaming">#4CAF50</color>
    <color name="status_error">#FF5252</color>
```

- [ ] **Step 3: Import de `Log` y `ContextCompat`**

```java
import android.util.Log;

import androidx.core.content.ContextCompat;
```

- [ ] **Step 4: Reescribir `setStatus` y añadir los helpers por estado**

Reemplazar el método `setStatus` actual:

```java
    private void setStatus(String text) {
        if (statusText != null) {
            statusText.setText(text);
        }
    }
```

por:

```java
    private static final String TAG = "IcuNgPlugin";

    private void setStatus(String text, int colorRes) {
        if (statusText == null) return;
        statusText.setText(text);
        statusText.setTextColor(ContextCompat.getColor(pluginContext, colorRes));
    }

    private void setStatusIdle() {
        setStatus(pluginContext.getString(R.string.status_idle), R.color.status_neutral);
    }

    private void setStatusConnecting() {
        setStatus(pluginContext.getString(R.string.status_connecting), R.color.status_connecting);
    }

    private void setStatusStreaming() {
        setStatus(pluginContext.getString(R.string.status_streaming), R.color.status_streaming);
    }

    private void setStatusDisconnected() {
        setStatus(pluginContext.getString(R.string.status_disconnected), R.color.status_neutral);
    }

    private void setStatusError(String message) {
        setStatus(message, R.color.status_error);
    }
```

- [ ] **Step 5: Sustituir las llamadas en `showPane()`**

```java
                setStatus(pluginContext.getString(R.string.status_streaming));
```
→
```java
                setStatusStreaming();
```

y

```java
                setStatus(pluginContext.getString(R.string.status_idle));
```
→
```java
                setStatusIdle();
```

- [ ] **Step 6: Sustituir la llamada en `onProtocolSelected()`**

```java
                setStatus(pluginContext.getString(R.string.status_error_format,
                        "detén la emisión antes de cambiar a/desde SRT"));
```
→
```java
                setStatusError(pluginContext.getString(
                        R.string.status_error_change_protocol_while_streaming));
```

- [ ] **Step 7: Sustituir las dos validaciones en `toggleStream()`**

```java
                setStatus(pluginContext.getString(R.string.status_error_format,
                        "la URL debe empezar por " + expectedScheme + "://"));
```
→
```java
                setStatusError(pluginContext.getString(
                        R.string.status_error_scheme_format, expectedScheme));
```

```java
                setStatus(pluginContext.getString(R.string.status_error_format,
                        "el passphrase SRT debe tener entre 10 y 79 caracteres"));
```
→
```java
                setStatusError(pluginContext.getString(
                        R.string.status_error_srt_passphrase_length));
```

- [ ] **Step 8: Reescribir los 6 métodos de `ConnectChecker`**

```java
    @Override
    public void onConnectionStarted(String url) {
        setStatusConnecting();
    }

    @Override
    public void onConnectionSuccess() {
        setStatusStreaming();
    }

    @Override
    public void onConnectionFailed(String reason) {
        Log.w(TAG, "Conexion fallida (detalle interno de RootEncoder): " + reason);
        setStatusError(pluginContext.getString(R.string.status_error_connection));
        stopStreamOnly();
    }

    @Override
    public void onDisconnect() {
        setStatusDisconnected();
        stopStreamOnly();
    }

    @Override
    public void onAuthError() {
        Log.w(TAG, "Fallo de autenticacion reportado por RootEncoder");
        setStatusError(pluginContext.getString(R.string.status_error_auth));
        stopStreamOnly();
    }

    @Override
    public void onAuthSuccess() {
        setStatusStreaming();
    }
```

- [ ] **Step 9: Compilar**

```powershell
Set-Location "D:\Documents\atak-plugins\tools\atak-plugin-dev"
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`. Si falla con `cannot find symbol
ContextCompat`, comprobar que `androidx.core` sigue resuelto a 1.17.0 en
`build.gradle` (no debería hacer falta tocar nada, ya está fijado desde la
Fase 0).

- [ ] **Step 10: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/res/values/strings.xml \
        icu-replacement/app/src/main/res/values/colors.xml \
        icu-replacement/app/src/main/java/com/carlosgt/atak/icung/plugin/IcuNgPlugin.java
git status --short
git commit -m "$(cat <<'EOF'
fix(icu-replacement): mensajes de estado neutros en espanol + color por estado

onConnectionFailed/onAuthError ya no muestran el texto interno de
RootEncoder (ingles, tecnico - "Shutdown received from server", etc.)
en el panel; el detalle sigue disponible via Log.w para depuracion.
Los 3 mensajes de validacion propios (esquema de URL, longitud de
passphrase, cambio de protocolo mientras se emite) pasan de
concatenarse en Java a vivir como recursos de string completos.
De paso, el texto de estado ahora cambia de color segun el estado
(neutro/amarillo/verde/rojo) para dar la misma señal de un vistazo
que ya se penso para el texto en la Fase 5, usando colors.xml que
ya existia desde la plantilla del SDK pero no se habia usado.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 2: Pulido visual del panel

**Files:**
- Modify: `icu-replacement/app/src/main/res/layout/main_layout.xml`
- Modify: `icu-replacement/app/src/main/res/values/arrays.xml`
- Modify: `icu-replacement/app/src/main/res/values/strings.xml`

**Interfaces:** Ninguna — solo layout y recursos; todos los `id` de vistas
que `IcuNgPlugin.java` busca con `findViewById` se mantienen igual (mismo
nombre, misma vista), así que no hace falta tocar el Java de esta Task.

Repasado con ojo crítico contra lo visto de verdad en el dispositivo
durante la verificación de la Fase 5:

- Las filas del panel están pegadas entre sí (el `padding="4dp"` es del
  contenedor entero, no hay margen entre filas) — se nota sobre todo entre
  la fila de protocolo/URL/botón y la fila de cifrado que aparece debajo.
- El spinner de protocolo muestra "RTSPS (TLS)" entero, que en un panel al
  50% de pantalla le quita espacio de sobra al campo de URL (la parte que
  de verdad importa leer) — el paréntesis "(TLS)" no aporta nada que el
  checkbox de certificados ya no deje claro en cuanto aparece.
- El hint del campo de passphrase SRT ("Passphrase SRT (10-79 caracteres,
  vacío = sin cifrar)") es justo el aviso que hace falta mientras se
  escribe — y el hint desaparece en cuanto se escribe la primera letra.
- El spinner de tipo de cifrado (AES-128/192/256) no tiene ninguna
  etiqueta — al lado de un campo de passphrase sin más contexto no es obvio
  qué es.
- El checkbox de "confiar en cualquier certificado" y el aviso de
  seguridad que lleva dentro se dejan tal cual — acortarlo perdería el
  aviso "(solo pruebas/LAN)", que es información de seguridad real, no
  relleno.

- [ ] **Step 1: Protocolos más cortos**

En `arrays.xml`:

```xml
    <string-array name="protocol_entries">
        <item>RTSP</item>
        <item>RTSPS (TLS)</item>
        <item>SRT</item>
    </string-array>
```
→
```xml
    <string-array name="protocol_entries">
        <item>RTSP</item>
        <item>RTSPS</item>
        <item>SRT</item>
    </string-array>
```

- [ ] **Step 2: Caption persistente + hint más corto para el passphrase SRT**

En `strings.xml`, junto a `srt_passphrase_hint`:

```xml
    <string name="srt_passphrase_hint">Passphrase SRT (10-79 caracteres, vacío = sin cifrar)</string>
```
→
```xml
    <string name="srt_passphrase_hint">Passphrase SRT</string>
    <string name="srt_passphrase_caption">10-79 caracteres · vacío = sin cifrar</string>
    <string name="srt_encryption_caption">Cifrado</string>
```

- [ ] **Step 3: Reescribir `main_layout.xml` completo**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.pedro.library.view.OpenGlView
        android:id="@+id/preview_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical"
        android:background="#80000000"
        android:padding="@dimen/auto_margin">

        <TextView
            android:id="@+id/status_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/status_neutral"
            android:textStyle="bold"
            android:paddingBottom="4dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/auto_margin"
            android:orientation="horizontal">

            <Spinner
                android:id="@+id/protocol_spinner"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:spinnerMode="dropdown" />

            <EditText
                android:id="@+id/rtsp_url_input"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginStart="4dp"
                android:inputType="textUri"
                android:hint="rtsp://host:puerto/stream"
                android:text="rtsp://10.0.2.2:8554/icung"
                android:textColor="#FFFFFF"
                android:textColorHint="#AAAAAA" />

            <Button
                android:id="@+id/stream_toggle_button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="4dp"
                android:text="@string/start_stream" />

        </LinearLayout>

        <CheckBox
            android:id="@+id/trust_all_certs_checkbox"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/auto_margin"
            android:text="@string/trust_all_certs"
            android:textColor="#FFFFFF"
            android:visibility="gone" />

        <LinearLayout
            android:id="@+id/srt_encryption_row"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/auto_margin"
            android:orientation="vertical"
            android:visibility="gone">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/srt_passphrase_caption"
                android:textColor="#AAAAAA"
                android:textSize="11sp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:orientation="horizontal">

                <EditText
                    android:id="@+id/srt_passphrase_input"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:inputType="textPassword"
                    android:hint="@string/srt_passphrase_hint"
                    android:textColor="#FFFFFF"
                    android:textColorHint="#AAAAAA" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="4dp"
                    android:layout_gravity="center_vertical"
                    android:text="@string/srt_encryption_caption"
                    android:textColor="#AAAAAA"
                    android:textSize="11sp" />

                <Spinner
                    android:id="@+id/srt_encryption_spinner"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="4dp"
                    android:spinnerMode="dropdown" />

            </LinearLayout>

        </LinearLayout>

    </LinearLayout>

</FrameLayout>
```

Cambios sobre el actual: márgenes entre filas (`auto_margin`, el `dimen`
de 6dp que ya existía sin usar), `status_text` usa `@color/status_neutral`
en vez de un `#FFFFFF` fijo (para que Task 1 pueda cambiarlo en tiempo de
ejecución sin pelearse con un valor por defecto distinto), y
`srt_encryption_row` pasa de una fila horizontal a una columna con el
caption del passphrase arriba y la fila passphrase+cifrado debajo — mismos
`id`, así que `IcuNgPlugin.java` no cambia.

- [ ] **Step 4: Compilar**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew assembleCivDebug --console=plain"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: `scan` y commit**

```powershell
docker compose exec atak-dev bash -lc "cd icu-replacement && ./gradlew clean --console=plain"
docker compose exec atak-dev scan icu-replacement
```

Expected: `Nothing blocking`.

```bash
cd "D:\Documents\atak-plugins"
git add icu-replacement/app/src/main/res/layout/main_layout.xml \
        icu-replacement/app/src/main/res/values/arrays.xml \
        icu-replacement/app/src/main/res/values/strings.xml
git status --short
git commit -m "$(cat <<'EOF'
style(icu-replacement): espaciado entre filas, protocolos mas cortos, aviso persistente de passphrase

Sin cambios de comportamiento ni de id de vistas. auto_margin (6dp,
ya existia en dimen.xml desde la plantilla del SDK, sin usar) entre
cada fila del panel en vez de quedar todas pegadas. "RTSPS (TLS)" pasa
a "RTSPS" para dejarle mas ancho al campo de URL en el panel al 50%
de pantalla - el checkbox de certificados ya dice "TLS" via el
contexto en cuanto aparece. El aviso de "10-79 caracteres" del
passphrase SRT, que antes vivia solo en el hint (desaparece al
escribir), pasa a un caption que queda visible siempre.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

## Task 3: Verificación en dispositivo

**Files:** ninguno.

- [ ] **Step 1: Deploy y sync+Load**

```powershell
docker compose exec atak-dev deploy icu-replacement
```

- [ ] **Step 2: Comprobar los 5 estados y su color**

Abrir el panel (estado "Detenido", color neutro). Emitir por RTSP contra un
receptor de prueba: "Conectando…" (amarillo) → "Emitiendo" (verde). Probar
un error real (URL con esquema equivocado para el protocolo elegido, o un
host que no responda) y confirmar que el mensaje es neutro en español
(nunca texto en inglés) y en rojo. Pulsar "Detener": "Desconectado" o
similar, vuelve a color neutro.

- [ ] **Step 3: Comprobar el aviso de passphrase y el resto del layout**

Seleccionar SRT, confirmar que el caption "10-79 caracteres · vacío = sin
cifrar" sigue visible después de escribir en el campo de passphrase (a
diferencia del hint, que desaparecería). Confirmar visualmente que las
filas ya no están pegadas entre sí y que el campo de URL se lee mejor con
"RTSPS"/"SRT" más cortos en el spinner.

- [ ] **Step 4: Log de depuración**

Con un fallo de conexión provocado (Step 2), confirmar en logcat que el
detalle técnico sigue apareciendo:

```powershell
docker compose exec atak-dev bash -lc 'adb logcat -d | grep IcuNgPlugin'
```

Expected: una línea `W IcuNgPlugin: Conexion fallida...` con el texto
interno de RootEncoder — confirma que el detalle no se perdió, solo se
sacó del panel.

Sin commit en esta tarea.

---

## Self-Review

- **Cobertura:** mensajes neutros (Task 1) + color por estado (Task 1,
  extensión natural pedida implícitamente al pulir el mismo texto) +
  espaciado/agrupación/claridad de protocolo (Task 2) + verificación real
  en dispositivo (Task 3).
- **Placeholders:** ninguno — layout, strings y Java completos en cada Step.
- **Fuera de alcance, deliberadamente:** no se toca la construcción de
  `camera`, `showPane()`/`tearDownPane()`, ni el envío CoT — nada de eso
  necesita cambiar para esto. El checkbox de "confiar en certificados" se
  deja con su texto largo a propósito (aviso de seguridad, no relleno).
