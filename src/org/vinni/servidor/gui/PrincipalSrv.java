package org.vinni.servidor.gui;

import org.vinni.dto.MiDatagrama;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor UDP. | Author: Vinni 2024 - Nathalie Pinzón 2026
 */
public class PrincipalSrv extends JFrame {

    private final int PORT = 12345;
    private DatagramSocket socketUDP; // Se crea un solo socket para todo el servidor, no por cliente como en TCP.
    private final Map<String, ClienteInfo> clientes = new ConcurrentHashMap<>(); // Almacena clientes registrados: nombre → ClienteInfo (IP + Puerto). En UDP no hay conexión, solo identificación por IP:Puerto.

    // GUI
    private JButton bIniciar;
    private JTextArea mensajesTxt;
    private JLabel lblClientes;

    // CONSTRUCTOR
    public PrincipalSrv() {
        initComponents();
    }

    private void initComponents() {
        setTitle("Servidor UDP");
        setSize(570, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- NORTE ---
        JPanel panelNorte = new JPanel(new BorderLayout(10, 0));
        panelNorte.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));

        JLabel titulo = new JLabel("SERVIDOR UDP");
        titulo.setFont(new Font("Dialog", Font.BOLD, 16));;
        titulo.setForeground(new Color(204, 0, 0));

        bIniciar = new JButton("INICIAR SERVIDOR");
        bIniciar.setFont(new Font("Dialog", Font.PLAIN, 14));
        bIniciar.addActionListener(e -> iniciarServidor());

        lblClientes = new JLabel("Clientes conectados: 0");
        lblClientes.setFont(new Font("Dialog", Font.PLAIN, 12));

        JPanel panelTitulo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelTitulo.add(titulo);
        panelTitulo.add(lblClientes);

        panelNorte.add(panelTitulo, BorderLayout.WEST);
        panelNorte.add(bIniciar, BorderLayout.EAST);

        // --- CENTRO ---
        mensajesTxt = new JTextArea();
        mensajesTxt.setEditable(false);
        mensajesTxt.setFont(new Font("Dialog", Font.PLAIN, 13));
        mensajesTxt.setLineWrap(true);

        JScrollPane scroll = new JScrollPane(mensajesTxt);
        scroll.setBorder(BorderFactory.createTitledBorder("Log del servidor"));

        add(panelNorte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    // INICIAR SERVIDOR
    private void iniciarServidor() {
        bIniciar.setEnabled(false);
        log("Iniciando servidor UDP en puerto " + PORT + "...");

        new Thread(() -> {
            try {
                socketUDP = new DatagramSocket(PORT);
                log("[OK]: Servidor activo. Esperando clientes...");

                while (true) {
                    // Esperar paquete entrante
                    DatagramPacket paquete = MiDatagrama.crearReceptor();
                    socketUDP.receive(paquete);

                    String mensaje   = MiDatagrama.extraerMensaje(paquete);
                    String ipOrigen  = MiDatagrama.extraerIP(paquete);
                    int puertoOrigen = MiDatagrama.extraerPuerto(paquete);

                    procesarMensaje(mensaje, ipOrigen, puertoOrigen);
                }

            } catch (SocketException ex) {
                Logger.getLogger(PrincipalSrv.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(PrincipalSrv.class.getName()).log(Level.SEVERE, null, ex);
            }
        }).start();
    }

    // MENSAJES ENTRANTES

    // La función principal de procesamiento de mensajes. Recibe el mensaje, la IP y el puerto del remitente.
    private void procesarMensaje(String mensaje, String ip, int puerto) {
        if (mensaje.startsWith("REGISTRO:")) {
            String nombre = mensaje.substring(9).trim();
            nombre = generarNombreUnico(nombre);
            clientes.put(nombre, new ClienteInfo(nombre, ip, puerto));
            actualizarContadorClientes();

            log("[ACTIVO] Cliente registrado: " + nombre + " [" + ip + ":" + puerto + "]");

            // Confirmar registro al cliente
            enviar(ip, puerto, "SERVIDOR: Bienvenido " + nombre);

            // Notificar a todos
            broadcast("SERVIDOR", nombre + " se ha conectado", null);
        }

        else if (mensaje.startsWith("MSG:")) {
            String[] partes = mensaje.split(":", 3);
            if (partes.length < 3) return;

            String destino = partes[1].trim();
            String texto   = partes[2].trim();

            String remitente = buscarRemitente(ip, puerto);

            if (destino.equals("*")) {
                // BROADCAST
                log("[BROADCAST] " + remitente + ": " + texto);
                broadcast(remitente, texto, null);
            } else {
                // PRIVADO → solo se muestra que hubo un mensaje, no el contenido
                log("[PRIVADO] " + remitente + " → " + destino + " [contenido oculto]");
                enviarPrivado(remitente, destino, texto);
            }
        }

        else if (mensaje.equals("LISTA")) {
            String remitente = buscarRemitente(ip, puerto);
            String lista = "Usuarios conectados: " + String.join(", ", clientes.keySet());
            enviar(ip, puerto, lista);
            log("📋 Lista enviada a " + remitente);
        }

        else if (mensaje.startsWith("DESCONECTAR:")) {
            String nombre = mensaje.substring(12).trim();
            clientes.remove(nombre);
            actualizarContadorClientes();
            log("[INACTIVO] Cliente desconectado: " + nombre);
            broadcast("SERVIDOR", nombre + " se ha desconectado", null);
        }

        else {
            log("[ALERTA] Mensaje desconocido de [" + ip + ":" + puerto + "]: " + mensaje);
        }
    }

    // BROADCAST: enviar a TODOS (excepto excluido si aplica)
    private void broadcast(String remitente, String texto, String excluir) {
        String msgFormateado = "[" + remitente + "]: " + texto;
        for (ClienteInfo cliente : clientes.values()) {
            if (excluir != null && cliente.nombre.equals(excluir)) continue;
            enviar(cliente.ip, cliente.puerto, msgFormateado);
        }
    }

    // MENSAJE PRIVADO
    private void enviarPrivado(String remitente, String destino, String texto) {
        ClienteInfo target = clientes.get(destino);
        if (target != null) {
            enviar(target.ip, target.puerto,
                    "[Privado de " + remitente + "]: " + texto);

            // Confirmar al remitente
            ClienteInfo origen = buscarClientePorIPPuerto(remitente);
            if (origen != null) {
                enviar(origen.ip, origen.puerto,
                        "[Privado → " + destino + "]: " + texto);
            }
        } else {
            // Notificar al remitente que el destino no existe
            ClienteInfo origenInfo = buscarClientePorIPPuerto(remitente);
            if (origenInfo != null) {
                enviar(origenInfo.ip, origenInfo.puerto,
                        "SERVIDOR: Usuario '" + destino + "' no encontrado");
            }
        }
    }

    // ENVÍO
    private void enviar(String ip, int puerto, String texto) {
        try {
            DatagramPacket paquete = MiDatagrama.crearDataG(ip, puerto, texto);
            if (paquete != null) {
                socketUDP.send(paquete);
            }
        } catch (IOException ex) {
            Logger.getLogger(PrincipalSrv.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Busca el nombre de un cliente por IP y Puerto */
    private String buscarRemitente(String ip, int puerto) {
        for (ClienteInfo c : clientes.values()) {
            if (c.ip.equals(ip) && c.puerto == puerto) return c.nombre;
        }
        return ip + ":" + puerto; // fallback si aún no está registrado
    }

    /** Busca un ClienteInfo por nombre de remitente */
    private ClienteInfo buscarClientePorIPPuerto(String nombre) {
        return clientes.get(nombre);
    }

    /** Genera nombre único si ya existe uno igual */
    private String generarNombreUnico(String base) {
        if (base == null || base.isBlank()) base = "cliente";
        String nombre = base;
        int i = 1;
        while (clientes.containsKey(nombre)) {
            nombre = base + "-" + i++;
        }
        return nombre;
    }

    /** Actualiza el contador de clientes en la GUI (thread-safe) */
    private void actualizarContadorClientes() {
        SwingUtilities.invokeLater(() ->
                lblClientes.setText("Clientes conectados: " + clientes.size())
        );
    }

    /** Escribe en el log (thread-safe) */
    private void log(String texto) {
        SwingUtilities.invokeLater(() -> {
            mensajesTxt.append(texto + "\n");
            mensajesTxt.setCaretPosition(mensajesTxt.getDocument().getLength());
        });
    }

    private static class ClienteInfo {
        String nombre;
        String ip;
        int puerto;

        ClienteInfo(String nombre, String ip, int puerto) {
            this.nombre = nombre;
            this.ip = ip;
            this.puerto = puerto;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
}