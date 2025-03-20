// ServerInfo.java
// Clase que almacena la información básica de un servidor: ID, host y puertos para comunicación.
public class ServerInfo {
    public int id;
    public String host;
    public int serverPort; // Puerto para comunicación entre servidores (Bully y sincronización)
    public int clientPort; // Puerto para atender clientes

    public ServerInfo(int id, String host, int serverPort, int clientPort) {
         this.id = id;
         this.host = host;
         this.serverPort = serverPort;
         this.clientPort = clientPort;
    }
}
