package org.vinni.dto;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DTO compartido entre Cliente y Servidor UDP., Gestiona la creación y parsing de DatagramPackets.

 * Author: Vinni 2024 | Nathalie Pinzón 2026
 */
public class MiDatagrama {

    public static final int BUFFER_SIZE = 4096;

    // CREACIÓN DE PAQUETES
    // Cre un DatagramPacket listo para enviar a una IP y puerto específicos, con un mensaje dado. Maneja excepciones internamente y retorna null si falla.

    public static DatagramPacket crearDataG(String ip, int puerto, String mensaje) {
        try {
            InetAddress direccion = InetAddress.getByName(ip);
            byte[] mensajeB = mensaje.getBytes();
            return new DatagramPacket(mensajeB, mensajeB.length, direccion, puerto);
        } catch (UnknownHostException ex) {
            Logger.getLogger(MiDatagrama.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }


    // Crea un DatagramPacket listo para enviar a una IP y puerto específicos, con un mensaje dado. Maneja excepciones internamente y retorna null si falla.
    public static DatagramPacket crearReceptor() {
        byte[] buf = new byte[BUFFER_SIZE];
        return new DatagramPacket(buf, buf.length);
    }

    // CONSTRUCCIÓN DE MENSAJES DE PROTOCOLO: Estos métodos ayudan a construir mensajes con el formato correcto para el protocolo definido (registro, mensaje, lista).
    public static String msgRegistro(String nombre) {
        return "REGISTRO:" + nombre;
    }

    public static String msgBroadcast(String texto) {
        return "MSG:*:" + texto;
    }

    public static String msgPrivado(String destinatario, String texto) {
        return "MSG:" + destinatario + ":" + texto;
    }

    public static String msgLista() {
        return "LISTA";
    }

    // EXTRACCIÓN DE DATOS DE UN PAQUETE RECIBIDO
    public static String extraerMensaje(DatagramPacket paquete) {
        return new String(paquete.getData(), 0, paquete.getLength()).trim();
    }

    public static String extraerIP(DatagramPacket paquete) {
        return paquete.getAddress().getHostAddress();
    }

    public static int extraerPuerto(DatagramPacket paquete) {
        return paquete.getPort();
    }
}