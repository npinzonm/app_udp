package org.vinni.cliente.gui;

import org.vinni.dto.MiDatagrama;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente UDP con dos modos de operación: Centralizado -> servidor reenvía todo, P2P -> clientes se envían mensajes DIRECTAMENTE.
 * Author: Vinni 2024 | Nathalie Pinzón 2026
 */
public class PrincipalCli extends JFrame {

    private final String IP_SERVIDOR = "127.0.0.1";
    private final int    PORT        = 12345;

    private DatagramSocket socketUDP;
    private String  miNombre;
    private boolean conectado = false;

    /** true = modo P2P, false = modo Centralizado */
    private boolean modoP2P = false;

    /**
     * Latch para sincronizar la respuesta de DIRECTORIO del servidor.
     * El hilo de escucha lo libera cuando llega DIR_RESP o IPS_RESP.
     */
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


    private void initComponents() {
        setTitle("Cliente UDP");
        setSize(950, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        // NORTE
        JPanel panelNorte = new JPanel(new BorderLayout(10, 0));
        panelNorte.setBorder(BorderFactory.createEmptyBorder(20, 25, 10, 25));

        JLabel instrucciones = new JLabel(
                "<html><b>Instrucciones:</b><br>" +
                        "1. Conectate al servidor &nbsp;<br> " +
                        "2. Elige modo CENTRALIZADO o P2P &nbsp; <br>" +
                        "3. Envia mensajes a todos o a un destino especifico</html>"
        );
        instrucciones.setFont(new Font("Dialog", Font.PLAIN, 12));

        conectar = new JButton("Conectar");
        conectar.setFont(new Font("Dialog", Font.PLAIN, 13));
        conectar.addActionListener(e -> conectar());

        lblEstado = new JLabel("Desconectado");
        lblEstado.setFont(new Font("Dialog", Font.BOLD, 12));
        lblEstado.setForeground(Color.GRAY);

        // Botón de modo — alterna entre CENTRALIZADO y P2P
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
        panelDerecho.add(panelEstado, BorderLayout.CENTER);
        panelDerecho.add(panelModoDesc, BorderLayout.SOUTH);

        panelNorte.add(instrucciones, BorderLayout.WEST);
        panelNorte.add(panelDerecho, BorderLayout.EAST);

        // CENTRO
        mensajes = new JTextArea();
        mensajes.setEditable(false);
        mensajes.setFont(new Font("Dialog", Font.PLAIN, 14));
        mensajes.setLineWrap(true);

        JScrollPane scroll = new JScrollPane(mensajes);
        scroll.setBorder(BorderFactory.createTitledBorder("Historial de mensajes"));

        // SUR
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
        add(scroll, BorderLayout.CENTER);
        add(panelSur, BorderLayout.SOUTH);
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

    private void conectar() {
        try {
            socketUDP = new DatagramSocket(); // puerto libre asignado por el SO
            socketUDP.setSoTimeout(0);

            String nombre = JOptionPane.showInputDialog(this,
                    "Ingresa tu nombre de usuario:", "Nombre", JOptionPane.QUESTION_MESSAGE);
            if (nombre == null || nombre.trim().isEmpty()) { socketUDP.close(); return; }

            miNombre = nombre.trim();
            setTitle("Cliente UDP - " + miNombre);

            // Registrar en el servidor
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

                    // Interceptar respuestas del directorio P2P
                    if (msg.startsWith("DIR_RESP:") || msg.startsWith("IPS_RESP:")) {
                        respuestaDir.set(msg);
                        CountDownLatch latch = latchDirectorio.get();
                        if (latch != null) latch.countDown();
                        continue; // no mostrar en el historial
                    }

                    // Mostrar en historial
                    log(msg);

                } catch (SocketException ex) {
                    break; // socket cerrado (desconexion)
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
        if (texto.isEmpty()) return;

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
            // Modo centralizado — igual que antes
            String msgFinal = esBroadcast
                    ? MiDatagrama.msgBroadcast(texto)
                    : MiDatagrama.msgPrivado(destinatario, texto);
            enviarAlServidor(msgFinal);
        }

        mensaje.setText("");
    }

    // ── LÓGICA P2P ────────────────────────────────────────────
    private void enviarP2P(String texto, String destinatario, boolean esBroadcast) {

        // Ejecutar en hilo separado para no bloquear la GUI
        new Thread(() -> {
            try {
                if (esBroadcast) {
                    enviarP2PBroadcast(texto);
                } else {
                    enviarP2PPrivado(destinatario, texto);
                }
            } catch (Exception e) {
                log("[ERROR P2P] " + e.getMessage());
            }
        }).start();
    }

    /**
     * PRIVADO P2P:
     * 1. Pide al servidor la IP:Puerto del destinatario
     * 2. Espera respuesta DIR_RESP (máx 3 segundos)
     * 3. Envía el mensaje DIRECTAMENTE al cliente destino
     */
    private void enviarP2PPrivado(String destinatario, String texto) throws Exception {

        // Paso 1: preparar latch para esperar la respuesta
        CountDownLatch latch = new CountDownLatch(1);
        latchDirectorio.set(latch);
        respuestaDir.set(null);

        // Paso 2: pedir directorio al servidor
        enviarAlServidor(MiDatagrama.msgDirectorio(destinatario));

        // Paso 3: esperar respuesta (timeout 3s)
        boolean recibido = latch.await(3, TimeUnit.SECONDS);
        latchDirectorio.set(null);

        if (!recibido || respuestaDir.get() == null) {
            log("[P2P] Tiempo de espera agotado — el servidor no respondio");
            return;
        }

        String resp = respuestaDir.get();

        if (!resp.startsWith("DIR_RESP:")) {
            log("[P2P] " + resp); // puede ser "SERVIDOR: Usuario no encontrado"
            return;
        }

        // Formato: DIR_RESP:ip:puerto
        String[] partes = resp.split(":", 3);
        String ipDest    = partes[1];
        int puertoDest   = Integer.parseInt(partes[2]);

        // Paso 4: enviar DIRECTO al cliente destino (sin pasar por servidor)
        String msgDireto = MiDatagrama.msgP2P(miNombre, texto);
        DatagramPacket paquete = MiDatagrama.crearDataG(ipDest, puertoDest, msgDireto);
        if (paquete != null) {
            socketUDP.send(paquete);
            log("[P2P -> " + destinatario + "]: " + texto);
        }

        // Paso 5: notificar al servidor solo para el log (sin contenido)
        enviarAlServidor("P2P_LOG:" + miNombre + ":PRIVADO:" + destinatario);
    }


    private void enviarP2PBroadcast(String texto) throws Exception {

        // Paso 1: preparar latch
        CountDownLatch latch = new CountDownLatch(1);
        latchDirectorio.set(latch);
        respuestaDir.set(null);

        // Paso 2: pedir lista de IPs al servidor
        enviarAlServidor(MiDatagrama.msgListaIPs());

        // Paso 3: esperar respuesta
        boolean recibido = latch.await(3, TimeUnit.SECONDS);
        latchDirectorio.set(null);

        if (!recibido || respuestaDir.get() == null) {
            log("[P2P-BC] Tiempo de espera agotado");
            return;
        }

        String resp = respuestaDir.get();

        if (!resp.startsWith("IPS_RESP:")) {
            log("[P2P-BC] Respuesta inesperada: " + resp);
            return;
        }

        // Formato: IPS_RESP:nombre:puerto,nombre:puerto,...
        String lista = resp.substring(9); // quitar "IPS_RESP:"

        if (lista.isEmpty()) {
            log("[P2P-BC] No hay otros clientes conectados");
            return;
        }

        String msgBc = MiDatagrama.msgP2PBroadcast(miNombre, texto);
        int enviados  = 0;

        for (String entrada : lista.split(",")) {
            String[] partes = entrada.split(":");
            if (partes.length < 2) continue;
            // IP siempre es 127.0.0.1 en localhost
            int puertoDest = Integer.parseInt(partes[1].trim());

            DatagramPacket paquete = MiDatagrama.crearDataG("127.0.0.1", puertoDest, msgBc);
            if (paquete != null) {
                socketUDP.send(paquete);
                enviados++;
            }
        }

        log("[P2P-BC] Mensaje enviado directo a " + enviados + " cliente(s)");

        // Notificar al servidor solo para el log
        enviarAlServidor("P2P_LOG:" + miNombre + ":BROADCAST:todos");
    }

    private void pedirLista() {
        if (!conectado) return;
        enviarAlServidor(MiDatagrama.msgLista());
    }

    // ── DESCONEXION ───────────────────────────────────────────
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

    // ── ENVIAR AL SERVIDOR ────────────────────────────────────
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