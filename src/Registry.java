// Registry.java
import java.io.*;
import java.net.*;
import java.util.*;

public class Registry {
    private static final int REGISTRY_PORT = 6000;
    // Lista de servidores registrados
    private static List<ServerInfo> servers = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("Registry iniciado en el puerto " + REGISTRY_PORT);
        try (ServerSocket serverSocket = new ServerSocket(REGISTRY_PORT)) {
            while(true) {
                Socket socket = serverSocket.accept();
                new Thread(new RegistryHandler(socket)).start();
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    // Manejador para cada conexión de registro.
    private static class RegistryHandler implements Runnable {
        private Socket socket;

        public RegistryHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            ) {
                String request = in.readLine();
                // Formato esperado: REGISTER;serverId;clientPort;serverPort
                if(request != null && request.startsWith("REGISTER")) {
                    String[] tokens = request.split(";");
                    if(tokens.length >= 4) {
                        int serverId = Integer.parseInt(tokens[1]);
                        int clientPort = Integer.parseInt(tokens[2]);
                        int serverPort = Integer.parseInt(tokens[3]);
                        String host = socket.getInetAddress().getHostAddress();
                        ServerInfo info = new ServerInfo(serverId, host, serverPort, clientPort);

                        // Agrega el servidor si no está registrado aún
                        boolean exists = false;
                        synchronized(servers) {
                            for(ServerInfo s : servers) {
                                if(s.id == serverId) {
                                    exists = true;
                                    break;
                                }
                            }
                            if(!exists) {
                                servers.add(info);
                                System.out.println("Registrado servidor: " + info.id);
                            }
                        }
                        // Envía la lista de servidores en formato:
                        // serverId,host,serverPort,clientPort|serverId,host,serverPort,clientPort|...
                        StringBuilder sb = new StringBuilder();
                        synchronized(servers) {
                            for(ServerInfo s : servers) {
                                sb.append(s.id).append(",")
                                  .append(s.host).append(",")
                                  .append(s.serverPort).append(",")
                                  .append(s.clientPort).append("|");
                            }
                        }
                        if(sb.length() > 0) {
                            sb.setLength(sb.length() - 1); // Remueve la última '|'
                        }
                        out.println(sb.toString());
                    } else {
                        out.println("ERROR: formato incorrecto");
                    }
                }
            } catch(IOException e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch(IOException e) {}
            }
        }
    }
}
