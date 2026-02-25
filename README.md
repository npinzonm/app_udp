# 💬 app-udp

App multiusuario en Java usando UDP con soporte para mensajes broadcast, privados y comunicación P2P directa entre clientes. Incluye servidor central y múltiples clientes con interfaz gráfica Swing.

---

## 📋 Descripción

Este proyecto implementa un sistema de chat en tiempo real usando el protocolo **UDP (User Datagram Protocol)** en Java. A diferencia de TCP, UDP no establece conexiones persistentes, por lo que el servidor actúa como intermediario central que mantiene un registro de todos los clientes conectados (nombre → IP:Puerto) y gestiona el enrutamiento de mensajes.

El sistema soporta **dos modos de operación** que el usuario puede alternar en tiempo real:

- **Modo Centralizado** — todos los mensajes pasan por el servidor.
- **Modo P2P** — el servidor actúa solo como directorio de puertos. El contenido viaja directamente entre clientes sin pasar por el servidor.

---

## 🏗️ Arquitectura

### Modo Centralizado
```
┌─────────────┐        REGISTRO / MSG        ┌─────────────────┐
│  Cliente A  │ ───────────────────────────▶ │                 │
└─────────────┘                              │  Servidor UDP   │
                                             │   Puerto 12345  │
┌─────────────┐        REGISTRO / MSG        │                 │
│  Cliente B  │ ───────────────────────────▶ │  ConcurrentMap  │
└─────────────┘                              │  nombre→IP:Port │
┌─────────────┐        REGISTRO / MSG        │                 │
│  Cliente C  │ ───────────────────────────▶ │                 │
└─────────────┘                              └─────────────────┘
```

### Modo P2P con Servidor de Directorio
```
┌─────────────┐   1. DIRECTORIO:Bob   ┌─────────────────┐
│  Cliente A  │ ─────────────────────▶│                 │
│             │ ◀─────────────────────│  Servidor UDP   │
│             │   2. DIR_RESP:ip:port │  (solo directorio)
└─────────────┘                       └─────────────────┘
       │
       │  3. P2P:Ana:Hola Bob   (DIRECTO — servidor no interviene)
       ▼
┌─────────────┐
│  Cliente B  │
└─────────────┘
```

El servidor posee **un único DatagramSocket** que recibe todos los paquetes. No hay un hilo por cliente como en TCP — el enrutamiento se hace por nombre de usuario almacenado en un `ConcurrentHashMap`.

---

## 📁 Estructura del proyecto

```
app-udp/
├── src/
│   └── org/vinni/
│       ├── dto/
│       │   └── MiDatagrama.java          ← DTO compartido (cliente y servidor)
│       ├── servidor/gui/
│       │   └── PrincipalSrv.java         ← Servidor UDP con GUI Swing
│       └── cliente/gui/
│           └── PrincipalCli.java         ← Cliente UDP con GUI Swing
└── README.md
```

---

## 🔌 Protocolo de mensajes

### Modo Centralizado

| Tipo | Formato | Descripción |
|---|---|---|
| Registro | `REGISTRO:nombre` | El cliente anuncia su nombre al servidor |
| Broadcast | `MSG:*:texto` | Mensaje para todos los clientes |
| Privado | `MSG:destino:texto` | Mensaje solo para un cliente específico |
| Lista | `LISTA` | Solicitar usuarios conectados |
| Desconexión | `DESCONECTAR:nombre` | Notificar salida al servidor |

### Modo P2P

| Tipo | Formato | Descripción |
|---|---|---|
| Pedir dirección | `DIRECTORIO:nombre` | Solicitar IP:Puerto de un cliente |
| Pedir lista IPs | `LISTA_IPS` | Solicitar puertos de todos (broadcast P2P) |
| Respuesta dir. | `DIR_RESP:ip:puerto` | Servidor responde con la dirección |
| Respuesta lista | `IPS_RESP:nom:puerto,...` | Servidor responde con todos los puertos |
| Privado directo | `P2P:remitente:texto` | Mensaje directo entre clientes |
| Broadcast directo | `P2P_BC:remitente:texto` | Broadcast directo entre clientes |
| Log actividad | `P2P_LOG:rem:tipo:dest` | Notifica al servidor (sin contenido) |

---

## ⚙️ Requisitos

- Java 11 o superior
- IDE recomendado: IntelliJ IDEA o NetBeans
- Sin dependencias externas (solo Java SE)

---

## 🚀 Cómo ejecutar

### 1. Clonar el repositorio

```bash
git clone https://github.com/npinzonm/app_udp.git
cd app_udp
```

### 2. Compilar

```bash
javac -d out src/org/vinni/dto/MiDatagrama.java \
             src/org/vinni/servidor/gui/PrincipalSrv.java \
             src/org/vinni/cliente/gui/PrincipalCli.java
```

### 3. Ejecutar el servidor

```bash
java -cp out org.vinni.servidor.gui.PrincipalSrv
```

### 4. Ejecutar clientes (en terminales separadas)

```bash
java -cp out org.vinni.cliente.gui.PrincipalCli
```

> Para abrir múltiples instancias en IntelliJ: `Run → Edit Configurations → Allow multiple instances`

---

## 🧪 Casos de uso

### Caso 1 — Broadcast centralizado (a todos)

1. Inicia el servidor → clic en **INICIAR SERVIDOR**
2. Abre 3 clientes → conecta cada uno con un nombre distinto (ej: `Ana`, `Bob`, `Carlos`)
3. En `Ana` escribe un mensaje → destino vacío o `*`
4. El mensaje llega a `Bob` y `Carlos`
5. El servidor muestra en **azul** el contenido completo del mensaje

```
+----- CENTRALIZADO - BROADCAST ---------------+
  De      : Ana
  Mensaje : Hola a todos!
  Ruta    : Cliente --> Puerto 12345 --> Todos
  SERVIDOR LEE Y REENVÍA EL CONTENIDO
+----------------------------------------------+
```

### Caso 2 — Privado centralizado (a uno específico)

1. Misma configuración anterior
2. En `Ana` escribe un mensaje → destino `Bob`
3. Solo `Bob` recibe el mensaje, `Carlos` no
4. El servidor muestra en **azul** el contenido completo

```
+----- CENTRALIZADO - PRIVADO -----------------+
  De      : Ana
  Para    : Bob
  Mensaje : Hola Bob!
  Ruta    : Cliente --> Puerto 12345 --> Bob
  SERVIDOR LEE Y REENVÍA EL CONTENIDO
+----------------------------------------------+
```

### Caso 3 — Privado P2P (directo, sin pasar por servidor)

1. En el cliente de `Ana` → clic en **Modo: CENTRALIZADO** → cambia a **Modo: P2P**
2. Escribe un mensaje → destino `Bob`
3. El servidor entrega el puerto de `Bob` a `Ana` y no interviene más
4. El mensaje viaja **directamente** de `Ana` a `Bob`
5. El servidor muestra en **verde** que el contenido no pasó por él

```
+----- P2P - PRIVADO DIRECTO -------------------+
  De        : Ana
  Para      : Bob
  Servidor  : entrego puerto 54892 y NO interviene mas
  Ruta msg  : Ana --DIRECTO--> Bob:54892
  CONTENIDO NO PASA POR PUERTO 12345
+----------------------------------------------+
```

### Caso 4 — Broadcast P2P (directo, sin pasar por servidor)

1. Modo P2P activo en `Ana`
2. Escribe un mensaje → destino vacío o `*`
3. El servidor entrega la lista de puertos de todos los clientes
4. `Ana` envía un paquete individual directo a cada cliente
5. El servidor muestra en **verde** que no transportó el contenido

```
+----- P2P - BROADCAST DIRECTO -----------------+
  De        : Ana
  Servidor  : entrego puertos de -> Bob(:54892), Carlos(:55103)
  Ruta msg  : Ana --DIRECTO--> cada cliente
  CONTENIDO NO PASA POR PUERTO 12345
+----------------------------------------------+
```

---

## 🔍 Diferencias clave entre modos

| Aspecto | Centralizado | P2P con Directorio |
|---|---|---|
| ¿Quién enruta los mensajes? | Servidor | Los propios clientes |
| ¿El servidor ve el contenido? | Sí | No |
| Puerto 12345 transporta el mensaje | Sí | Solo la solicitud de dirección |
| Color en log del servidor | Azul | Verde |
| Privacidad del contenido | Baja | Alta |

---

## 🔍 Diferencias clave con la versión TCP

| Aspecto | TCP | UDP |
|---|---|---|
| Conexión | Persistente por socket | Sin conexión |
| Hilo por cliente | ✅ `ClientHandler` | ❌ Un solo hilo receptor |
| Identificación | Por socket | Por IP + Puerto en mapa |
| Envío de archivos | Stream continuo | ❌ No implementado en UDP |
| Privacidad mensajes privados | Contenido visible en servidor | Solo metadata visible |

---

## ⚠️ Limitaciones conocidas

- En localhost todos los clientes comparten la IP `127.0.0.1`. El P2P se simula por puertos distintos. En una red real con múltiples equipos funcionaría sin cambios.
- El modo P2P requiere que el servidor esté activo para resolver direcciones. Si el servidor cae, el P2P queda inutilizable.
- UDP no garantiza entrega ni orden de paquetes. En red local esto no presenta problemas prácticos.
- El timeout de espera de respuesta del directorio es fijo en 3 segundos.
- No hay cifrado. Los mensajes viajan en texto plano.

---

## 👤 Autor

**Vinni** — 2024 | 
**Nathalie Pinzón** - 2026

---

## 📄 Licencia

Este proyecto está licenciado bajo GNU General Public License v3.0 (GPL v3).

Esto significa que:
- Puedes usar, estudiar y modificar el código libremente.
- Si distribuyes versiones modificadas, deben hacerse bajo la misma licencia GPL v3.
- El código fuente siempre debe estar disponible para quien reciba el programa.

Este proyecto es una mejora sustancial sobre una base académica preexistente.
Ver el texto completo en: https://www.gnu.org/licenses/gpl-3.0.html
