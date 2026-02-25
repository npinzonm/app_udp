# 💬 udp-chat-java

Chat multiusuario en Java usando UDP con soporte para mensajes broadcast y privados. Incluye servidor central y múltiples clientes con interfaz gráfica Swing.

---

## 📋 Descripción

Este proyecto implementa un sistema de chat en tiempo real usando el protocolo **UDP (User Datagram Protocol)** en Java. A diferencia de TCP, UDP no establece conexiones persistentes, por lo que el servidor actúa como intermediario central que mantiene un registro de todos los clientes conectados (nombre → IP:Puerto) y gestiona el enrutamiento de mensajes.

---

## 🏗️ Arquitectura

```
┌─────────────┐        REGISTRO / MSG        ┌─────────────────┐
│  Cliente A  │ ───────────────────────────▶ │                 │
└─────────────┘                              │  Servidor UDP   │
                                             │   Puerto 12345  │
┌─────────────┐        REGISTRO / MSG        │                 │
│  Cliente B  │ ───────────────────────────▶ │  ConcurrentMap  │
└─────────────┘                              │  nombre→IP:Port │
                                             │                 │
┌─────────────┐        REGISTRO / MSG        │                 │
│  Cliente C  │ ───────────────────────────▶ │                 │
└─────────────┘                              └─────────────────┘
```

El servidor posee **un único DatagramSocket** que recibe todos los paquetes. No hay un hilo por cliente como en TCP — el enrutamiento se hace por nombre de usuario almacenado en un `ConcurrentHashMap`.

---

## 📁 Estructura del proyecto

```
udp-chat-java/
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

Todos los mensajes son texto plano con el siguiente formato:

| Tipo | Formato | Descripción |
|---|---|---|
| Registro | `REGISTRO:nombre` | El cliente anuncia su nombre al servidor |
| Broadcast | `MSG:*:texto` | Mensaje para todos los clientes |
| Privado | `MSG:destino:texto` | Mensaje solo para un cliente específico |
| Lista | `LISTA` | Solicitar usuarios conectados |
| Desconexión | `DESCONECTAR:nombre` | Notificar salida al servidor |

---

## ⚙️ Requisitos

- Java 11 o superior
- IDE recomendado: IntelliJ IDEA o NetBeans
- Sin dependencias externas (solo Java SE)

---

## 🚀 Cómo ejecutar

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/udp-chat-java.git
cd udp-chat-java
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

> Puedes abrir tantos clientes como quieras. Cada uno corre en su propia instancia.

---

## 🧪 Casos de uso

### Caso 1 — Mensaje broadcast (a todos)

1. Inicia el servidor → clic en **INICIAR SERVIDOR**
2. Abre 3 clientes → conecta cada uno con un nombre distinto (ej: `Ana`, `Bob`, `Carlos`)
3. En `Ana` escribe un mensaje → cuando pregunte destino, deja vacío o escribe `*`
4. El mensaje llega a `Bob` y `Carlos`

```
[Ana]: Hola a todos!        ← Bob y Carlos lo ven
```

### Caso 2 — Mensaje privado (a uno específico)

1. Misma configuración anterior
2. En `Ana` escribe un mensaje → cuando pregunte destino, escribe `Bob`
3. Solo `Bob` recibe el mensaje. `Carlos` no lo ve.
4. En el servidor se registra la actividad **sin mostrar el contenido**:

```
[PRIVADO] Ana → Bob [contenido oculto]
```

---

## 🔍 Diferencias clave con la versión TCP

| Aspecto | TCP | UDP |
|---|---|---|
| Conexión | Persistente por socket | Sin conexión |
| Hilo por cliente | ✅ `ClientHandler` | ❌ Un solo hilo receptor |
| Identificación | Por socket | Por IP + Puerto en mapa |
| Envío de archivos | Stream continuo | ❌ No implementado en UDP |
| Privacidad | Contenido visible en servidor | Solo metadata visible |

---

## 👤 Autor

**Vinni** — 2024

---

## 📄 Licencia

MIT License — libre para usar, modificar y distribuir.