package org.vinni.cliente.gui;

import org.vinni.dto.MiDatagrama;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente UDP.
 * Author: Vinni 2024 | Nathalie Pinzón 2026
 */
public class PrincipalCli extends JFrame {

    private final String IP_SERVIDOR = "127.0.0.1";
    private final int PORT = 12345;

    private DatagramSocket socketUDP;
    private String miNombre;
    private boolean conectado = false;

    // GUI
    private JTextArea mensajes;
    private JTextField mensaje;
    private JButton conectar;
    private JButton enviarTexto;
    private JButton pedirLista;
    private JLabel lblEstado;

    public PrincipalCli() {
        initComponents();
    }


    private void initComponents() {
        setTitle("Cliente UDP");
        setSize(950, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        // ── NORTE ────────────────────────────────────────────
        JPanel panelNorte = new JPanel(new BorderLayout(10, 0));
        panelNorte.setBorder(BorderFactory.createEmptyBorder(20, 25, 10, 25));

        JLabel instrucciones = new JLabel(
                "<html><b>Instrucciones:</b><br>" +
                        "1. Conéctate al servidor<br>" +
                        "2. Ingresa tu nombre<br>" +
                        "3. Envía mensajes a todos o a un destino específico</html>"
        );

        conectar = new JButton("Conectar a Servidor");
        conectar.setFont(new Font("Dialog", Font.PLAIN, 13));
        conectar.addActionListener(e -> conectar());

        lblEstado = new JLabel("[OFF] Desconectado");
        lblEstado.setFont(new Font("Dialog", Font.PLAIN, 12));
        lblEstado.setForeground(Color.GRAY);

        JPanel panelBotNorte = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBotNorte.add(lblEstado);
        panelBotNorte.add(conectar);

        panelNorte.add(instrucciones, BorderLayout.WEST);
        panelNorte.add(panelBotNorte, BorderLayout.EAST);

        // ── CENTRO ───────────────────────────────────────────
        mensajes = new JTextArea();
        mensajes.setEditable(false);
        mensajes.setFont(new Font("Dialog", Font.PLAIN, 14));
        mensajes.setLineWrap(true);

        JScrollPane scroll = new JScrollPane(mensajes);
        scroll.setBorder(BorderFactory.createTitledBorder("Historial de mensajes"));

        // ── SUR ──────────────────────────────────────────────
        JPanel panelSur = new JPanel(new BorderLayout(10, 0));
        panelSur.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));

        mensaje = new JTextField();
        mensaje.setFont(new Font("Dialog", Font.PLAIN, 14));
        mensaje.addActionListener(e -> enviarMensaje());

        enviarTexto = new JButton("ENVIAR TEXTO");
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

    // CONEXIÓN / REGISTRO
    private void conectar() {
        try {
            // Crear socket UDP (puerto 0 = asignado por el SO automáticamente)
            socketUDP = new DatagramSocket();
            socketUDP.setSoTimeout(0); // sin timeout → bloquea esperando

            String nombre = JOptionPane.showInputDialog(this,
                    "Ingresa tu nombre de usuario:",
                    "Nombre",
                    JOptionPane.QUESTION_MESSAGE);

            if (nombre == null || nombre.trim().isEmpty()) {
                socketUDP.close();
                return;
            }

            miNombre = nombre.trim();
            setTitle("Cliente UDP - " + miNombre);

            // Enviar mensaje de registro
            String msgReg = MiDatagrama.msgRegistro(miNombre);
            DatagramPacket paquete = MiDatagrama.crearDataG(IP_SERVIDOR, PORT, msgReg);
            socketUDP.send(paquete);

            conectado = true;
            conectar.setEnabled(false);
            enviarTexto.setEnabled(true);
            pedirLista.setEnabled(true);
            lblEstado.setText("[ON] Conectado como: " + miNombre);
            lblEstado.setForeground(new Color(0, 150, 0));

            // Iniciar hilo de escucha
            escuchar();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo conectar al servidor.\n" + e.getMessage());
        }
    }

    // HILO DE ESCUCHA  (igual al TCP pero con DatagramSocket)
    private void escuchar() {
        new Thread(() -> {
            while (conectado) {
                try {
                    DatagramPacket paquete = MiDatagrama.crearReceptor();
                    socketUDP.receive(paquete);
                    String msgRecibido = MiDatagrama.extraerMensaje(paquete);

                    SwingUtilities.invokeLater(() -> {
                        mensajes.append(msgRecibido + "\n");
                        mensajes.setCaretPosition(mensajes.getDocument().getLength());
                    });

                } catch (SocketException ex) {
                    // Socket cerrado (desconexión voluntaria)
                    break;
                } catch (IOException ex) {
                    Logger.getLogger(PrincipalCli.class.getName())
                            .log(Level.SEVERE, null, ex);
                }
            }
        }).start();
    }

    // ENVIAR MENSAJE
    private void enviarMensaje() {
        if (!conectado) {
            JOptionPane.showMessageDialog(this, "No estás conectado");
            return;
        }

        String texto = mensaje.getText().trim();
        if (texto.isEmpty()) return;

        // Igual que TCP: preguntar destino
        String destinatario = JOptionPane.showInputDialog(
                this,
                "¿Destino del mensaje?\n• * o vacío = todos\n• nombre = privado",
                "Enviar mensaje",
                JOptionPane.QUESTION_MESSAGE
        );

        if (destinatario == null) return;
        destinatario = destinatario.trim();

        String msgFinal;
        if (destinatario.isEmpty() || destinatario.equals("*")) {
            msgFinal = MiDatagrama.msgBroadcast(texto);
        } else {
            msgFinal = MiDatagrama.msgPrivado(destinatario, texto);
        }

        enviarAlServidor(msgFinal);
        mensaje.setText("");
    }

    // LISTA DE USUARIOS
    private void pedirLista() {
        if (!conectado) return;
        enviarAlServidor(MiDatagrama.msgLista());
    }

    // DESCONEXIÓN
    private void desconectar() {
        if (!conectado) return;

        // Avisar al servidor
        enviarAlServidor("DESCONECTAR:" + miNombre);

        conectado = false;
        socketUDP.close();

        conectar.setEnabled(true);
        enviarTexto.setEnabled(false);
        pedirLista.setEnabled(false);
        lblEstado.setText("[OFF] Desconectado");
        lblEstado.setForeground(Color.GRAY);
        setTitle("Cliente UDP");

        SwingUtilities.invokeLater(() ->
                mensajes.append("[OUT] Desconectado del servidor\n")
        );
    }

    // ENVÍO AL SERVIDOR
    private void enviarAlServidor(String texto) {
        try {
            DatagramPacket paquete = MiDatagrama.crearDataG(IP_SERVIDOR, PORT, texto);
            if (paquete != null) {
                socketUDP.send(paquete);
            }
        } catch (IOException ex) {
            Logger.getLogger(PrincipalCli.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}