package org.vinni.servidor.gui;

import org.vinni.dto.MiDatagrama;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * Author: Vinni 2024 | Nathalie Pinzón 2026
 */
public class PrincipalSrv extends JFrame {

    private final int PORT = 12345;

    /** Timeout de inactividad en minutos */
    private static final int TIMEOUT_MINUTOS = 2;

    /** Palabras reservadas — no se pueden usar como nombre de usuario */
    private static final Set<String> PALABRAS_RESERVADAS =
            new HashSet<>(Arrays.asList("SERVIDOR", "TODOS", "*"));

    /** Regex nombre valido: letras, numeros y _, entre 2 y 20 caracteres */
    private static final String REGEX_NOMBRE = "^[a-zA-Z0-9_]{2,20}$";

    private DatagramSocket socketUDP;
    private final Map<String, ClienteInfo> clientes = new ConcurrentHashMap<>();

    /** Scheduler para revisar timeouts de inactividad cada 30 segundos */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // ── Colores del log ───────────────────────────────────────
    private static final Color COLOR_CENTRALIZADO = new Color(0, 80, 180);
    private static final Color COLOR_P2P          = new Color(0, 140, 50);
    private static final Color COLOR_SISTEMA      = new Color(100, 100, 100);
    private static final Color COLOR_SEPARADOR    = new Color(180, 180, 180);
    private static final Color COLOR_VALIDACION   = new Color(160, 80, 0);   // Naranja oscuro
    private static final Color COLOR_ERROR        = new Color(180, 0, 0);

    // ── GUI ───────────────────────────────────────────────────
    private JButton   bIniciar;
    private JTextPane logPane;
    private JLabel    lblClientes;

    public PrincipalSrv() { initComponents(); }

    // ── INIT GUI ─────────────────────────────────────────────
    private void initComponents() {
        setTitle("Servidor UDP");
        setSize(680, 520);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel panelNorte = new JPanel(new BorderLayout(10, 0));
        panelNorte.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));

        JLabel titulo = new JLabel("SERVIDOR UDP");
        titulo.setFont(new Font("Dialog", Font.BOLD, 16));
        titulo.setForeground(new Color(204, 0, 0));

        lblClientes = new JLabel("Clientes: 0");
        lblClientes.setFont(new Font("Dialog", Font.PLAIN, 12));

        JPanel leyenda = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leyenda.add(etiquetaColor("CENTRALIZADO", COLOR_CENTRALIZADO));
        leyenda.add(etiquetaColor("P2P",          COLOR_P2P));
        leyenda.add(etiquetaColor("VALIDACION",   COLOR_VALIDACION));
        leyenda.add(etiquetaColor("SISTEMA",      COLOR_SISTEMA));

        bIniciar = new JButton("INICIAR SERVIDOR");
        bIniciar.setFont(new Font("Dialog", Font.PLAIN, 14));
        bIniciar.addActionListener(e -> iniciarServidor());

        JPanel panelInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panelInfo.add(titulo);
        panelInfo.add(lblClientes);

        JPanel panelTop = new JPanel(new BorderLayout());
        panelTop.add(panelInfo, BorderLayout.WEST);
        panelTop.add(bIniciar,  BorderLayout.EAST);

        panelNorte.add(panelTop, BorderLayout.NORTH);
        panelNorte.add(leyenda,  BorderLayout.SOUTH);

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(new Font("Dialog", Font.PLAIN, 13));
        logPane.setBackground(new Color(250, 250, 252));

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createTitledBorder("Log del servidor"));

        add(panelNorte, BorderLayout.NORTH);
        add(scroll,     BorderLayout.CENTER);
    }

    private JLabel etiquetaColor(String texto, Color color) {
        JLabel lbl = new JLabel("■ " + texto);
        lbl.setFont(new Font("Dialog", Font.BOLD, 11));
        lbl.setForeground(color);
        return lbl;
    }

    // ── INICIAR ───────────────────────────────────────────────
    private void iniciarServidor() {
        bIniciar.setEnabled(false);
        logSistema("Iniciando servidor UDP en puerto " + PORT + "...");
        logSistema("Timeout de inactividad: " + TIMEOUT_MINUTOS + " minutos");

        // Revisar timeouts cada 30 segundos
        scheduler.scheduleAtFixedRate(this::revisarTimeouts, 30, 30, TimeUnit.SECONDS);

        new Thread(() -> {
            try {
                socketUDP = new DatagramSocket(PORT);
                logSistema("[OK] Servidor activo. Esperando clientes...");
                logSeparador();

                while (true) {
                    DatagramPacket paquete = MiDatagrama.crearReceptor();
                    socketUDP.receive(paquete);
                    String msg    = MiDatagrama.extraerMensaje(paquete);
                    String ip     = MiDatagrama.extraerIP(paquete);
                    int    puerto = MiDatagrama.extraerPuerto(paquete);
                    procesarMensaje(msg, ip, puerto);
                }
            } catch (SocketException ex) {
                Logger.getLogger(PrincipalSrv.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(PrincipalSrv.class.getName()).log(Level.SEVERE, null, ex);
            }
        }).start();
    }

    // ── PROCESAR MENSAJES ─────────────────────────────────────
    private void procesarMensaje(String mensaje, String ip, int puerto) {

        // Actualizar timestamp de actividad si el cliente ya esta registrado
        actualizarActividad(ip, puerto);

        // ── REGISTRO ─────────────────────────────────────────
        if (mensaje.startsWith("REGISTRO:")) {
            String nombreSolicitado = mensaje.substring(9).trim();

            // VALIDACION 1: caracteres permitidos
            if (!nombreSolicitado.matches(REGEX_NOMBRE)) {
                enviar(ip, puerto, "SERVIDOR: Nombre invalido. Solo letras, numeros y _ (2-20 caracteres)");
                logValidacion("[VALIDACION] Nombre rechazado: '" + nombreSolicitado
                        + "' — caracteres no permitidos [" + ip + ":" + puerto + "]");
                return;
            }

            // VALIDACION 2: palabras reservadas
            if (PALABRAS_RESERVADAS.contains(nombreSolicitado.toUpperCase())) {
                enviar(ip, puerto, "SERVIDOR: El nombre '" + nombreSolicitado + "' es una palabra reservada");
                logValidacion("[VALIDACION] Nombre rechazado: '" + nombreSolicitado
                        + "' — palabra reservada [" + ip + ":" + puerto + "]");
                return;
            }

            // VALIDACION 3: cliente duplicado por IP:Puerto
            String nombreExistente = buscarNombre(ip, puerto);
            if (!nombreExistente.equals(ip + ":" + puerto)) {
                clientes.remove(nombreExistente);
                String nuevoNombre = generarNombreUnico(nombreSolicitado);
                clientes.put(nuevoNombre, new ClienteInfo(nuevoNombre, ip, puerto));
                actualizarContador();
                enviar(ip, puerto, "SERVIDOR: Reconectado como " + nuevoNombre);
                logValidacion("[VALIDACION] IP:Puerto duplicado — " + nombreExistente
                        + " reemplazado por " + nuevoNombre);
                logSeparador();
                return;
            }

            String nombre = generarNombreUnico(nombreSolicitado);
            clientes.put(nombre, new ClienteInfo(nombre, ip, puerto));
            actualizarContador();
            logSistema("[+] Registrado: " + nombre + "  [" + ip + ":" + puerto + "]");
            enviar(ip, puerto, "SERVIDOR: Bienvenido " + nombre);
            broadcast("SERVIDOR", nombre + " se ha conectado", null);
            logSeparador();
        }

        // ── CENTRALIZADO: MSG ─────────────────────────────────
        else if (mensaje.startsWith("MSG:")) {
            String[] partes  = mensaje.split(":", 3);
            if (partes.length < 3) return;
            String destino   = partes[1].trim();
            String texto     = partes[2].trim();
            String remitente = buscarNombre(ip, puerto);

            // VALIDACION 4: mensaje vacio
            if (texto.isEmpty()) {
                enviar(ip, puerto, "SERVIDOR: No se permite enviar mensajes vacios");
                logValidacion("[VALIDACION] Mensaje vacio rechazado de " + remitente);
                return;
            }

            if (destino.equals("*")) {
                logCentralizado("----------- CENTRALIZADO - BROADCAST -------------");
                logCentralizado("  De      : " + remitente);
                logCentralizado("  Mensaje : \"" + texto + "\"");
                logCentralizado("  Ruta    : Cliente -> Puerto 12345 -> Todos");
                logCentralizado("  SERVIDOR LEE Y REENVÍA EL CONTENIDO");
                logCentralizado("--------------------------------------------------");
                broadcast(remitente, texto, null);
            } else {
                // VALIDACION 5: destino inexistente
                if (!clientes.containsKey(destino)) {
                    enviar(ip, puerto, "SERVIDOR: El usuario '" + destino + "' no existe o no esta conectado");
                    logValidacion("[VALIDACION] Destino inexistente: '" + destino + "' solicitado por " + remitente);
                    logSeparador();
                    return;
                }
                logCentralizado("---------- CENTRALIZADO - PRIVADO ----------------");
                logCentralizado("  De      : " + remitente);
                logCentralizado("  Para    : " + destino);
                logCentralizado("  Mensaje : \"" + texto + "\"");
                logCentralizado("  Ruta    : Cliente -> Puerto 12345 -> " + destino);
                logCentralizado("  SERVIDOR LEE Y REENVÍA EL CONTENIDO");
                logCentralizado("--------------------------------------------------");
                enviarPrivado(remitente, destino, texto);
            }
            logSeparador();
        }

        // ── P2P: directorio privado ───────────────────────────
        else if (mensaje.startsWith("DIRECTORIO:")) {
            String buscado   = mensaje.substring(11).trim();
            String remitente = buscarNombre(ip, puerto);
            ClienteInfo dest = clientes.get(buscado);

            if (dest != null) {
                enviar(ip, puerto, "DIR_RESP:" + dest.ip + ":" + dest.puerto);
                logP2P("+----- P2P - PRIVADO DIRECTO -------------------+");
                logP2P("  De        : " + remitente);
                logP2P("  Para      : " + buscado);
                logP2P("  Servidor  : entrego puerto " + dest.puerto + " y NO interviene mas");
                logP2P("  Ruta msg  : " + remitente + " --DIRECTO--> " + buscado + ":" + dest.puerto);
                logP2P("  CONTENIDO NO PASA POR PUERTO 12345");
                logP2P("+----------------------------------------------+");
            } else {
                enviar(ip, puerto, "SERVIDOR: Usuario '" + buscado + "' no encontrado");
                logValidacion("[VALIDACION] P2P — destino '" + buscado + "' no existe, solicitado por " + remitente);
            }
            logSeparador();
        }

        // ── P2P: lista IPs broadcast ──────────────────────────
        else if (mensaje.equals("LISTA_IPS")) {
            String remitente = buscarNombre(ip, puerto);
            String lista = clientes.values().stream()
                    .filter(c -> !(c.ip.equals(ip) && c.puerto == puerto))
                    .map(c -> c.nombre + ":" + c.puerto)
                    .collect(Collectors.joining(","));
            enviar(ip, puerto, "IPS_RESP:" + lista);

            String listaLegible = clientes.values().stream()
                    .filter(c -> !(c.ip.equals(ip) && c.puerto == puerto))
                    .map(c -> c.nombre + "(:" + c.puerto + ")")
                    .collect(Collectors.joining(", "));
            logP2P("+----- P2P - BROADCAST DIRECTO -----------------+");
            logP2P("  De        : " + remitente);
            logP2P("  Servidor  : entrego puertos de -> " + listaLegible);
            logP2P("  Ruta msg  : " + remitente + " --DIRECTO--> cada cliente");
            logP2P("  CONTENIDO NO PASA POR PUERTO 12345");
            logP2P("+----------------------------------------------+");
            logSeparador();
        }

        // ── P2P: confirmacion de envio directo ────────────────
        else if (mensaje.startsWith("P2P_LOG:")) {
            String[] partes = mensaje.split(":", 4);
            if (partes.length >= 4) {
                logP2P("  [P2P confirmado] " + partes[1]
                        + " -> " + partes[3]
                        + " | tipo: " + partes[2]
                        + " | contenido nunca llego al servidor");
            }
        }

        // ── LISTA ─────────────────────────────────────────────
        else if (mensaje.equals("LISTA")) {
            String remitente = buscarNombre(ip, puerto);
            enviar(ip, puerto, "Usuarios: " + String.join(", ", clientes.keySet()));
            logSistema("[LISTA] Enviada a " + remitente);
        }

        // ── DESCONEXION ───────────────────────────────────────
        else if (mensaje.startsWith("DESCONECTAR:")) {
            String nombre = mensaje.substring(12).trim();
            clientes.remove(nombre);
            actualizarContador();
            logSistema("[-] Desconectado: " + nombre);
            broadcast("SERVIDOR", nombre + " se ha desconectado", null);
            logSeparador();
        }

        else {
            log("[?] " + mensaje, COLOR_ERROR);
        }
    }

    // ── TIMEOUT DE INACTIVIDAD ────────────────────────────────
    private void revisarTimeouts() {
        long ahora    = System.currentTimeMillis();
        long limiteMs = TIMEOUT_MINUTOS * 60 * 1000L;

        List<String> expirados = new ArrayList<>();
        for (ClienteInfo c : clientes.values()) {
            if (ahora - c.ultimaActividad > limiteMs) expirados.add(c.nombre);
        }
        for (String nombre : expirados) {
            clientes.remove(nombre);
            actualizarContador();
            logValidacion("[TIMEOUT] " + nombre + " removido por inactividad ("
                    + TIMEOUT_MINUTOS + " min)");
            broadcast("SERVIDOR", nombre + " fue desconectado por inactividad", null);
            logSeparador();
        }
    }

    /** Actualiza el timestamp del ultimo paquete recibido de un cliente */
    private void actualizarActividad(String ip, int puerto) {
        for (ClienteInfo c : clientes.values()) {
            if (c.ip.equals(ip) && c.puerto == puerto) {
                c.ultimaActividad = System.currentTimeMillis();
                return;
            }
        }
    }

    // ── BROADCAST centralizado ────────────────────────────────
    private void broadcast(String remitente, String texto, String excluir) {
        String msg = "[" + remitente + "]: " + texto;
        for (ClienteInfo c : clientes.values()) {
            if (excluir != null && c.nombre.equals(excluir)) continue;
            enviar(c.ip, c.puerto, msg);
        }
    }

    // ── PRIVADO centralizado ──────────────────────────────────
    private void enviarPrivado(String remitente, String destino, String texto) {
        ClienteInfo target = clientes.get(destino);
        if (target != null) {
            enviar(target.ip, target.puerto, "[Privado de " + remitente + "]: " + texto);
            ClienteInfo origen = clientes.get(remitente);
            if (origen != null)
                enviar(origen.ip, origen.puerto, "[Privado -> " + destino + "]: " + texto);
        } else {
            ClienteInfo origen = clientes.get(remitente);
            if (origen != null)
                enviar(origen.ip, origen.puerto,
                        "SERVIDOR: Usuario '" + destino + "' no encontrado");
        }
    }

    // ── ENVIO UNITARIO ────────────────────────────────────────
    private void enviar(String ip, int puerto, String texto) {
        try {
            DatagramPacket p = MiDatagrama.crearDataG(ip, puerto, texto);
            if (p != null) socketUDP.send(p);
        } catch (IOException ex) {
            Logger.getLogger(PrincipalSrv.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // ── LOG CON COLORES ───────────────────────────────────────
    private void logCentralizado(String t)  { log(t, COLOR_CENTRALIZADO); }
    private void logP2P(String t)           { log(t, COLOR_P2P); }
    private void logSistema(String t)       { log(t, COLOR_SISTEMA); }
    private void logValidacion(String t)    { log(t, COLOR_VALIDACION); }
    private void logSeparador()             { log("", COLOR_SEPARADOR); }

    private void log(String texto, Color color) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = logPane.getStyledDocument();
            Style style = logPane.addStyle("estilo", null);
            StyleConstants.setForeground(style, color);
            StyleConstants.setFontFamily(style, "Dialog");
            StyleConstants.setFontSize(style, 13);
            try {
                doc.insertString(doc.getLength(), texto + "\n", style);
                logPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) { e.printStackTrace(); }
        });
    }

    // ── UTILIDADES ────────────────────────────────────────────
    private String buscarNombre(String ip, int puerto) {
        for (ClienteInfo c : clientes.values())
            if (c.ip.equals(ip) && c.puerto == puerto) return c.nombre;
        return ip + ":" + puerto;
    }

    private String generarNombreUnico(String base) {
        if (base == null || base.isBlank()) base = "cliente";
        if (!clientes.containsKey(base)) return base;
        int i = 1;
        while (clientes.containsKey(base + i)) i++;
        return base + i;
    }

    private void actualizarContador() {
        SwingUtilities.invokeLater(() ->
                lblClientes.setText("Clientes: " + clientes.size()));
    }

    // ── CLASE INTERNA ─────────────────────────────────────────
    private static class ClienteInfo {
        String nombre; String ip; int puerto;
        long   ultimaActividad;

        ClienteInfo(String n, String i, int p) {
            nombre = n; ip = i; puerto = p;
            ultimaActividad = System.currentTimeMillis();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
}