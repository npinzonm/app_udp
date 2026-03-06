package org.vinni.dto;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DTO compartido entre Cliente y Servidor UDP.
 *
 * Protocolo CENTRALIZADO:
 *   REGISTRO:nombre          → Cliente anuncia su nombre
 *   MSG:*:texto              → Broadcast (servidor reenvía)
 *   MSG:destino:texto        → Privado (servidor reenvía)
 *   LISTA                    → Lista de clientes
 *   DESCONECTAR:nombre       → Notificar salida
 *
 * Protocolo P2P:
 *   DIRECTORIO:nombre        → Pedir IP:Puerto de un cliente
 *   LISTA_IPS                → Pedir IP:Puerto de todos (broadcast P2P)
 *   P2P:remitente:texto      → Mensaje directo entre clientes
 *   P2P_BC:remitente:texto   → Broadcast directo entre clientes
 *
 * Respuestas servidor modo P2P:
 *   DIR_RESP:ip:puerto       → Respuesta a DIRECTORIO
 *   IPS_RESP:nom:puerto,...  → Respuesta a LISTA_IPS
 *
 * Author: Vinni 2024 | Nathalie Pinzón 2026
 */
public class MiDatagrama {

    public static final int BUFFER_SIZE = 4096;

    // ── Creación de paquetes ──────────────────────────────────

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

    public static DatagramPacket crearReceptor() {
        byte[] buf = new byte[BUFFER_SIZE];
        return new DatagramPacket(buf, buf.length);
    }

    // ── Modo Centralizado ─────────────────────────────────────

    public static String msgRegistro(String nombre)              { return "REGISTRO:" + nombre; }
    public static String msgBroadcast(String texto)              { return "MSG:*:" + texto; }
    public static String msgPrivado(String dest, String texto)   { return "MSG:" + dest + ":" + texto; }
    public static String msgLista()                              { return "LISTA"; }
    public static String msgDesconectar(String nombre)           { return "DESCONECTAR:" + nombre; }

    // ── Modo P2P ──────────────────────────────────────────────

    /** Pide al servidor la IP:Puerto de un cliente específico */
    public static String msgDirectorio(String nombre)            { return "DIRECTORIO:" + nombre; }

    /** Pide al servidor la lista completa de puertos (para broadcast P2P) */
    public static String msgListaIPs()                           { return "LISTA_IPS"; }

    /** Mensaje privado directo cliente→cliente (no pasa por servidor) */
    public static String msgP2P(String remitente, String texto)  { return "P2P:" + remitente + ":" + texto; }

    /** Broadcast directo cliente→clientes (no pasa por servidor) */
    public static String msgP2PBroadcast(String rem, String txt) { return "P2P_BC:" + rem + ":" + txt; }

    // ── Extracción ────────────────────────────────────────────

    public static String extraerMensaje(DatagramPacket p) {
        return new String(p.getData(), 0, p.getLength()).trim();
    }
    public static String extraerIP(DatagramPacket p)     { return p.getAddress().getHostAddress(); }
    public static int    extraerPuerto(DatagramPacket p) { return p.getPort(); }
}