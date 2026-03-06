package org.vinni.cliente.gui;

import org.vinni.dto.MiDatagrama;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Author: Vinni 2024 | Nathalie Pinzón 2026
 */
public class PrincipalCli extends JFrame {

    private final String IP_SERVIDOR = "127.0.0.1";
    private final int    PORT        = 12345;

    /** Regex nombre valido: letras, numeros y _, entre 2 y 20 caracteres */
    private static final String REGEX_NOMBRE = "^[a-zA-Z0-9_]{2,20}$";

    private DatagramSocket socketUDP;
    private String  miNombre;
    private boolean conectado = false;
    private boolean modoP2P   = false;

    private final AtomicReference<CountDownLatch> latchDirectorio = new AtomicReference<>(null);
    private final AtomicReference<String>         respuestaDir    = new AtomicReference<>(null);

    // ── GUI ──────────────────────────────────────────────────
    private JTextArea  mensajes;
    private JTextField mensaje;
    private JButton    conectar;
    private JButton    enviarTexto;
    private JButton    pedirLista;
    private JButton    btnModo;
    private JLabel     lblEstado;
    private JLabel     lblModo;

    public PrincipalCli() { initComponents(); }

    // ── INIT GUI ─────────────────────────────────────────────
    private void initComponents() {
        setTitle("Cliente UDP");
        setSize(950, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        JPanel panelNorte = new JPanel(new BorderLayout(10, 0));
        panelNorte.setBorder(BorderFactory.createEmptyBorder(20, 25, 10, 25));

        JLabel instrucciones = new JLabel(
                "<html><b>Instrucciones:</b><br>" +
                        "1. Conectate al servidor &nbsp;<br>" +
                        "2. Elige modo CENTRALIZADO o P2P &nbsp;<br>" +
                        "3. Envia mensajes a todos o a un destino especifico</html>"
        );
        instrucciones.setFont(new Font("Dialog", Font.PLAIN, 12));

        conectar = new JButton("Conectar");
        conectar.setFont(new Font("Dialog", Font.PLAIN, 13));
        conectar.addActionListener(e -> conectar());

        lblEstado = new JLabel("Desconectado");
        lblEstado.setFont(new Font("Dialog", Font.BOLD, 12));
        lblEstado.setForeground(Color.GRAY);

        btnModo = new JButton("Modo: CENTRALIZADO");
        btnModo.setFont(new Font("Dialog", Font.BOLD, 12));
        btnModo.setBackground(new Color(220, 235, 255));
        btnModo.setEnabled(false);
        btnModo.addActionListener(e -> alternarModo());

        lblModo = new JLabel("Los mensajes pasan por el servidor");
        lblModo.setFont(new Font("Dialog", Font.ITALIC, 11));
        lblModo.setForeground(new Color(80, 80, 80));

        JPanel panelEstado = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panelEstado.add(lblEstado);
        panelEstado.add(conectar);
        panelEstado.add(btnModo);

        JPanel panelModoDesc = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        panelModoDesc.add(lblModo);

        JPanel panelDerecho = new JPanel(new BorderLayout());
        panelDerecho.add(panelEstado,  BorderLayout.CENTER);
        panelDerecho.add(panelModoDesc,BorderLayout.SOUTH);

        panelNorte.add(instrucciones, BorderLayout.WEST);
        panelNorte.add(panelDerecho,  BorderLayout.EAST);

        mensajes = new JTextArea();
        mensajes.setEditable(false);
        mensajes.setFont(new Font("Dialog", Font.PLAIN, 14));
        mensajes.setLineWrap(true);

        JScrollPane scroll = new JScrollPane(mensajes);
        scroll.setBorder(BorderFactory.createTitledBorder("Historial de mensajes"));

        JPanel panelSur = new JPanel(new BorderLayout(10, 0));
        panelSur.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));

        mensaje = new JTextField();
        mensaje.setFont(new Font("Dialog", Font.PLAIN, 14));
        mensaje.addActionListener(e -> enviarMensaje());

        enviarTexto = new JButton("ENVIAR");
        enviarTexto.setEnabled(false);
        enviarTexto.addActionListener(e -> enviarMensaje());

        pedirLista = new JButton("VER USUARIOS");
        pedirLista.setEnabled(false);
        pedirLista.addActionListener(e -> pedirLista());

        JButton desconectar = new JButton("DESCONECTAR");
        desconectar.addActionListener(e -> desconectar());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(pedirLista);
        botones.add(enviarTexto);
        botones.add(desconectar);

        panelSur.add(mensaje, BorderLayout.CENTER);
        panelSur.add(botones, BorderLayout.EAST);

        add(panelNorte, BorderLayout.NORTH);
        add(scroll,     BorderLayout.CENTER);
        add(panelSur,   BorderLayout.SOUTH);
    }

    // ── ALTERNAR MODO ─────────────────────────────────────────
    private void alternarModo() {
        modoP2P = !modoP2P;
        if (modoP2P) {
            btnModo.setText("Modo: P2P");
            btnModo.setBackground(new Color(210, 255, 210));
            lblModo.setText("Mensajes privados/broadcast van DIRECTO (servidor no los ve)");
            lblModo.setForeground(new Color(0, 130, 0));
            log("[MODO] Cambiado a P2P — mensajes directos entre clientes");
        } else {
            btnModo.setText("Modo: CENTRALIZADO");
            btnModo.setBackground(new Color(220, 235, 255));
            lblModo.setText("Los mensajes pasan por el servidor");
            lblModo.setForeground(new Color(80, 80, 80));
            log("[MODO] Cambiado a CENTRALIZADO — mensajes via servidor");
        }
    }

    // ── CONEXION ──────────────────────────────────────────────
    private void conectar() {
        try {
            socketUDP = new DatagramSocket();
            socketUDP.setSoTimeout(0);

            String nombre = JOptionPane.showInputDialog(this,
                    "Ingresa tu nombre de usuario:", "Nombre", JOptionPane.QUESTION_MESSAGE);
            if (nombre == null || nombre.trim().isEmpty()) { socketUDP.close(); return; }

            miNombre = nombre.trim();

            // VALIDACION 1: caracteres permitidos
            if (!miNombre.matches(REGEX_NOMBRE)) {
                JOptionPane.showMessageDialog(this,
                        "Nombre invalido.\nSolo se permiten letras, numeros y _ (2-20 caracteres).",
                        "Nombre no valido", JOptionPane.WARNING_MESSAGE);
                socketUDP.close();
                return;
            }

            // VALIDACION 2: palabras reservadas
            String nombreUpper = miNombre.toUpperCase();
            if (nombreUpper.equals("SERVIDOR") || nombreUpper.equals("TODOS") || miNombre.equals("*")) {
                JOptionPane.showMessageDialog(this,
                        "El nombre '" + miNombre + "' es una palabra reservada del sistema.",
                        "Nombre no permitido", JOptionPane.WARNING_MESSAGE);
                socketUDP.close();
                return;
            }

            setTitle("Cliente UDP - " + miNombre);
            enviarAlServidor(MiDatagrama.msgRegistro(miNombre));

            conectado = true;
            conectar.setEnabled(false);
            enviarTexto.setEnabled(true);
            pedirLista.setEnabled(true);
            btnModo.setEnabled(true);
            lblEstado.setText("[ON] Conectado: " + miNombre);
            lblEstado.setForeground(new Color(0, 150, 0));

            escuchar();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo conectar: " + e.getMessage());
        }
    }

    private void escuchar() {
        new Thread(() -> {
            while (conectado) {
                try {
                    DatagramPacket paquete = MiDatagrama.crearReceptor();
                    socketUDP.receive(paquete);
                    String msg = MiDatagrama.extraerMensaje(paquete);

                    if (msg.startsWith("DIR_RESP:") || msg.startsWith("IPS_RESP:")) {
                        respuestaDir.set(msg);
                        CountDownLatch latch = latchDirectorio.get();
                        if (latch != null) latch.countDown();
                        continue;
                    }

                    log(msg);

                } catch (SocketException ex) {
                    break;
                } catch (IOException ex) {
                    Logger.getLogger(PrincipalCli.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }).start();
    }

    // ── ENVIAR MENSAJE ────────────────────────────────────────
    private void enviarMensaje() {
        if (!conectado) { JOptionPane.showMessageDialog(this, "No estas conectado"); return; }

        String texto = mensaje.getText().trim();

        // VALIDACION 3: mensaje vacio
        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No puedes enviar un mensaje vacio.",
                    "Mensaje vacio", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String destinatario = JOptionPane.showInputDialog(
                this,
                "Destino del mensaje:\n  * o vacio = todos\n  nombre    = privado",
                "Enviar mensaje", JOptionPane.QUESTION_MESSAGE);

        if (destinatario == null) return;
        destinatario = destinatario.trim();
        boolean esBroadcast = destinatario.isEmpty() || destinatario.equals("*");

        if (modoP2P) {
            enviarP2P(texto, destinatario, esBroadcast);
        } else {
            enviarAlServidor(esBroadcast
                    ? MiDatagrama.msgBroadcast(texto)
                    : MiDatagrama.msgPrivado(destinatario, texto));
        }

        mensaje.setText("");
    }

    // ── P2P ───────────────────────────────────────────────────
    private void enviarP2P(String texto, String destinatario, boolean esBroadcast) {
        new Thread(() -> {
            try {
                if (esBroadcast) enviarP2PBroadcast(texto);
                else             enviarP2PPrivado(destinatario, texto);
            } catch (Exception e) {
                log("[ERROR P2P] " + e.getMessage());
            }
        }).start();
    }

    private void enviarP2PPrivado(String destinatario, String texto) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        latchDirectorio.set(latch);
        respuestaDir.set(null);

        enviarAlServidor(MiDatagrama.msgDirectorio(destinatario));
        boolean recibido = latch.await(3, TimeUnit.SECONDS);
        latchDirectorio.set(null);

        if (!recibido || respuestaDir.get() == null) {
            log("[P2P] Tiempo de espera agotado — el servidor no respondio"); return;
        }

        String resp = respuestaDir.get();
        if (!resp.startsWith("DIR_RESP:")) {
            log("[P2P] " + resp); return;
        }

        String[] partes = resp.split(":", 3);
        int puertoDest  = Integer.parseInt(partes[2]);

        DatagramPacket paquete = MiDatagrama.crearDataG("127.0.0.1", puertoDest,
                MiDatagrama.msgP2P(miNombre, texto));
        if (paquete != null) {
            socketUDP.send(paquete);
            log("[P2P -> " + destinatario + "]: " + texto);
        }
        enviarAlServidor("P2P_LOG:" + miNombre + ":PRIVADO:" + destinatario);
    }

    private void enviarP2PBroadcast(String texto) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        latchDirectorio.set(latch);
        respuestaDir.set(null);

        enviarAlServidor(MiDatagrama.msgListaIPs());
        boolean recibido = latch.await(3, TimeUnit.SECONDS);
        latchDirectorio.set(null);

        if (!recibido || respuestaDir.get() == null) { log("[P2P-BC] Timeout"); return; }

        String resp = respuestaDir.get();
        if (!resp.startsWith("IPS_RESP:")) { log("[P2P-BC] Respuesta inesperada: " + resp); return; }

        String lista = resp.substring(9);
        if (lista.isEmpty()) { log("[P2P-BC] No hay otros clientes conectados"); return; }

        String msgBc = MiDatagrama.msgP2PBroadcast(miNombre, texto);
        int enviados = 0;
        for (String entrada : lista.split(",")) {
            String[] partes = entrada.split(":");
            if (partes.length < 2) continue;
            int puertoDest = Integer.parseInt(partes[1].trim());
            DatagramPacket paquete = MiDatagrama.crearDataG("127.0.0.1", puertoDest, msgBc);
            if (paquete != null) { socketUDP.send(paquete); enviados++; }
        }
        log("[P2P-BC] Mensaje enviado directo a " + enviados + " cliente(s)");
        enviarAlServidor("P2P_LOG:" + miNombre + ":BROADCAST:todos");
    }

    // ── RESTO ─────────────────────────────────────────────────
    private void pedirLista() {
        if (!conectado) return;
        enviarAlServidor(MiDatagrama.msgLista());
    }

    private void desconectar() {
        if (!conectado) return;
        enviarAlServidor(MiDatagrama.msgDesconectar(miNombre));
        conectado = false;
        socketUDP.close();
        conectar.setEnabled(true);
        enviarTexto.setEnabled(false);
        pedirLista.setEnabled(false);
        btnModo.setEnabled(false);
        lblEstado.setText("[OFF] Desconectado");
        lblEstado.setForeground(Color.GRAY);
        setTitle("Cliente UDP");
        log("Desconectado del servidor");
    }

    private void enviarAlServidor(String texto) {
        try {
            DatagramPacket p = MiDatagrama.crearDataG(IP_SERVIDOR, PORT, texto);
            if (p != null) socketUDP.send(p);
        } catch (IOException ex) {
            Logger.getLogger(PrincipalCli.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void log(String texto) {
        SwingUtilities.invokeLater(() -> {
            mensajes.append(texto + "\n");
            mensajes.setCaretPosition(mensajes.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}