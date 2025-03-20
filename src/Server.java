// Server.java
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private int serverId;            // Identificador del servidor
    private int clientPort;          // Puerto para clientes
    private int serverPort;          // Puerto para comunicación entre servidores
    private boolean isLeader;        // Indica si es líder
    private int leaderId;            // ID del líder actual
    private List<ServerInfo> otherServers;  // Lista de otros servidores
    private Map<String, Part> inventory;    // Inventario de refacciones

    public Server(int serverId, int clientPort, int serverPort, List<ServerInfo> otherServers) {
         this.serverId = serverId;
         this.clientPort = clientPort;
         this.serverPort = serverPort;
         this.otherServers = otherServers;
         this.isLeader = false;
         this.leaderId = -1;
         inventory = new ConcurrentHashMap<>();
         // Inicialización del inventario con ejemplos
         inventory.put("freno", new Part("1", "Freno", 10, 50.0));
         inventory.put("aceite", new Part("2", "Aceite", 20, 30.0));
    }

    public void start() {
         new Thread(() -> listenForClients()).start();
         new Thread(() -> listenForServers()).start();
         new Thread(() -> monitorLeader()).start();
         try { Thread.sleep(2000); } catch(Exception e) {}
         startElection();
    }
    
    private void listenForClients() {
         try (ServerSocket serverSocket = new ServerSocket(clientPort)) {
              while(true) {
                   Socket clientSocket = serverSocket.accept();
                   new Thread(() -> handleClient(clientSocket)).start();
              }
         } catch(IOException e) {
              e.printStackTrace();
         }
    }
    
    private void handleClient(Socket clientSocket) {
         try (
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
         ) {
             String request;
             while ((request = in.readLine()) != null) {
                  System.out.println("Servidor " + serverId + " recibió petición del cliente: " + request);
                  String response = processClientRequest(request);
                  out.println(response);
                  out.println("FIN");
             }
         } catch(IOException e) {
             e.printStackTrace();
         } finally {
             try { clientSocket.close(); } catch(IOException e) {}
         }
    }
    
    private String processClientRequest(String request) {
         String[] parts = request.split(";");
         String command = parts[0];
         if(command.equalsIgnoreCase("LIST")) {
              return getInventoryList();
         } else if(command.equalsIgnoreCase("BUY")) {
              if(!isLeader) {
                   return forwardToLeader(request);
              } else {
                   if(parts.length < 3) return "Error: comando BUY mal formado.";
                   String partName = parts[1];
                   int qty = Integer.parseInt(parts[2]);
                   Part p = inventory.get(partName.toLowerCase());
                   if(p == null) return "Error: refacción no encontrada.";
                   if(p.getQuantity() < qty) return "Error: cantidad insuficiente.";
                   p.setQuantity(p.getQuantity() - qty);
                   broadcastUpdate("UPDATE;" + partName + ";" + p.getQuantity());
                   return "Compra realizada. Nueva cantidad de " + partName + ": " + p.getQuantity();
              }
         } else {
              return "Error: comando desconocido.";
         }
    }
    
    private String forwardToLeader(String request) {
         ServerInfo leaderInfo = null;
         for(ServerInfo s : otherServers) {
              if(s.id == leaderId) {
                  leaderInfo = s;
                  break;
              }
         }
         if(leaderInfo == null) return "Error: Líder desconocido.";
         try (Socket socket = new Socket(leaderInfo.host, leaderInfo.clientPort);
              BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
              PrintWriter out = new PrintWriter(socket.getOutputStream(), true);) {
              out.println(request);
              StringBuilder sb = new StringBuilder();
              String line;
              while((line = in.readLine()) != null && !line.equals("FIN")){
                  sb.append(line).append("\n");
              }
              return sb.toString().trim();
         } catch(IOException e) {
              e.printStackTrace();
              return "Error: no se pudo conectar con el líder.";
         }
    }
    
    private String getInventoryList() {
         StringBuilder sb = new StringBuilder();
         for(Part p : inventory.values()) {
              sb.append(p.toString()).append("\n");
         }
         return sb.toString().trim();
    }
    
    private void listenForServers() {
         try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
              while(true) {
                   Socket socket = serverSocket.accept();
                   new Thread(() -> handleServerMessage(socket)).start();
              }
         } catch(IOException e) {
              e.printStackTrace();
         }
    }
    
    private void handleServerMessage(Socket socket) {
         try (
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
         ) {
              String message = in.readLine();
              if(message == null) return;
              System.out.println("Servidor " + serverId + " recibió mensaje de servidor: " + message);
              String[] tokens = message.split(";");
              String msgType = tokens[0];
              if(msgType.equalsIgnoreCase("ELECTION")) {
                   out.println("ANSWER;" + serverId);
                   startElection();
              } else if(msgType.equalsIgnoreCase("ANSWER")) {
                   // Manejo opcional
              } else if(msgType.equalsIgnoreCase("COORDINATOR")) {
                   leaderId = Integer.parseInt(tokens[1]);
                   isLeader = (leaderId == serverId);
                   System.out.println("Servidor " + serverId + " reconoce al líder: " + leaderId);
              } else if(msgType.equalsIgnoreCase("UPDATE")) {
                   if(tokens.length >= 3) {
                        String partName = tokens[1];
                        int newQty = Integer.parseInt(tokens[2]);
                        Part p = inventory.get(partName.toLowerCase());
                        if(p != null) {
                             p.setQuantity(newQty);
                        }
                   }
              }
         } catch(IOException e) {
             // Se omite para conexiones problemáticas
         } finally {
             try { socket.close(); } catch(IOException e) {}
         }
    }
    
    private void broadcastUpdate(String updateMsg) {
         for(ServerInfo s : otherServers) {
              try (Socket socket = new Socket(s.host, s.serverPort);
                   PrintWriter out = new PrintWriter(socket.getOutputStream(), true);) {
                   out.println(updateMsg);
              } catch(IOException e) {
                   System.out.println("No se pudo conectar al servidor " + s.id + " para actualizar.");
              }
         }
    }
    
    private void startElection() {
         System.out.println("Servidor " + serverId + " iniciando elección.");
         boolean higherResponded = false;
         for(ServerInfo s : otherServers) {
              if(s.id > serverId) {
                   try (Socket socket = new Socket(s.host, s.serverPort);
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));) {
                        out.println("ELECTION;" + serverId);
                        socket.setSoTimeout(2000);
                        String response = in.readLine();
                        if(response != null && response.startsWith("ANSWER")) {
                             higherResponded = true;
                        }
                   } catch(IOException e) {
                        System.out.println("No se pudo contactar al servidor " + s.id + " en la elección.");
                   }
              }
         }
         if(!higherResponded) {
              isLeader = true;
              leaderId = serverId;
              System.out.println("Servidor " + serverId + " se declara líder.");
              broadcastCoordinator();
         }
    }
    
    private void broadcastCoordinator() {
         for(ServerInfo s : otherServers) {
              try (Socket socket = new Socket(s.host, s.serverPort);
                   PrintWriter out = new PrintWriter(socket.getOutputStream(), true);) {
                   out.println("COORDINATOR;" + serverId);
              } catch(IOException e) {
                   System.out.println("No se pudo notificar al servidor " + s.id + " del nuevo líder.");
              }
         }
    }
    
    private void monitorLeader() {
         while(true) {
              try {
                   Thread.sleep(5000);
                   if(leaderId != serverId && leaderId != -1) {
                        ServerInfo leaderInfo = null;
                        for(ServerInfo s : otherServers) {
                             if(s.id == leaderId) {
                                  leaderInfo = s;
                                  break;
                             }
                        }
                        if(leaderInfo != null) {
                             try (Socket socket = new Socket(leaderInfo.host, leaderInfo.serverPort)) {
                                  // El líder responde correctamente.
                             } catch(IOException e) {
                                  System.out.println("El líder " + leaderId + " no responde. Iniciando nueva elección.");
                                  startElection();
                             }
                        }
                   }
              } catch(InterruptedException e) {
                   e.printStackTrace();
              }
         }
    }
    
    // Método main modificado para registrarse dinámicamente en el Registry.
    // Uso: java Server <serverId> <clientPort> <serverPort>
    public static void main(String[] args) {
         if(args.length < 3) {
              System.out.println("Uso: java Server <serverId> <clientPort> <serverPort>");
              return;
         }
         int serverId = Integer.parseInt(args[0]);
         int clientPort = Integer.parseInt(args[1]);
         int serverPort = Integer.parseInt(args[2]);
         List<ServerInfo> others = new ArrayList<>();
         
         // Se asume que el Registry está en localhost, puerto 6000.
         try (Socket socket = new Socket("localhost", 6000);
              BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
              PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
              out.println("REGISTER;" + serverId + ";" + clientPort + ";" + serverPort);
              String response = in.readLine();
              if(response != null && !response.isEmpty()){
                  // Formato: serverId,host,serverPort,clientPort|...
                  String[] serverEntries = response.split("\\|");
                  for(String entry : serverEntries){
                      String[] tokens = entry.split(",");
                      int id = Integer.parseInt(tokens[0]);
                      String host = tokens[1];
                      int sPort = Integer.parseInt(tokens[2]);
                      int cPort = Integer.parseInt(tokens[3]);
                      if(id != serverId) { // Excluir a sí mismo
                          others.add(new ServerInfo(id, host, sPort, cPort));
                      }
                  }
              }
         } catch(IOException e) {
              System.out.println("Error al registrar en el Registry: " + e.getMessage());
         }
         
         Server server = new Server(serverId, clientPort, serverPort, others);
         server.start();
    }
}
