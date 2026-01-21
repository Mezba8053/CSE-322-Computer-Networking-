import java.net.Socket;
import java.net.ServerSocket;
import java.io.IOException;
import java.util.*;

public class Server {
    private static List<String> clientNames = new ArrayList<>();
    private static Map<Socket, String> clientSockets = new HashMap<>();
    private static List<String> offlineClients = new ArrayList<>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(6666);
            System.out.println("Server is listening on port 6666");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // System.out.println("New client connected: " +
                // clientSocket.getInetAddress().getHostAddress());

                Thread worker = new Worker(clientSocket, clientNames, clientSockets, offlineClients);
                worker.start();
            }
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }
    }
}