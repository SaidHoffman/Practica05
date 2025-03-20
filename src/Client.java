// Client.java
import java.io.*;
import java.net.*;

public class Client {
    public static void main(String[] args) {
         if(args.length < 2) {
              System.out.println("Uso: java Client <serverHost> <serverClientPort>");
              return;
         }
         String host = args[0];
         int port = Integer.parseInt(args[1]);
         try (
              Socket socket = new Socket(host, port);
              BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
              PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
              BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
         ) {
              System.out.println("Conectado al servidor. Escribe comandos (ej: LIST o BUY;freno;2):");
              String userInput;
              while ((userInput = stdIn.readLine()) != null) {
                   out.println(userInput);
                   String line;
                   while ((line = in.readLine()) != null && !line.equals("FIN")) {
                        System.out.println("Respuesta del servidor: " + line);
                   }
              }
         } catch(IOException e) {
              e.printStackTrace();
         }
    }
}
