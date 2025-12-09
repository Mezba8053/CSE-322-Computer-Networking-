package Offline;
import java.net.*;
import java.io.*;

public class proxy {
    public static void main(String[] args) {
        try {
            ServerSocket proxySocket = new ServerSocket(6666);
            System.out.println("Proxy server is listening on port 6666");
            while (true) {
                Socket clientSocket = proxySocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                // Handle proxying in a separate thread or method
            }
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }
        // Create a proxy server
    }
}
