package Offline;

import java.net.*;
import java.io.*;

public class client {
    static ObjectOutputStream out;
    static ObjectInputStream in;
    static String fileName = "";
    static int fileSize = 0;
    static String fileType = "";
    static int chunkSize = 0;

    static void showMenu() {
        System.out.println("Capability List: ");
        System.out.println("1. Show Connected Client Lists:");
        System.out.println("2. Looking for Uploaded Files:");
        System.out.println("3. Upload File to Server:");
        System.out.println("4. Download File from Server:");
        System.out.println("5. Looking for Public Files:");
        System.out.println("6. Looking for Private Files(Request needed):");
        System.out.println("7. Looking for Unread Messages:");
        System.out.println("8. Upload & Download History:");
        System.out.println("9. Exit");
        System.out.println("Select your desired capability option number:");
    }

    static void handleOwnFiles() {
        try {
            String ownFiles = (String) in.readObject();
            System.out.println("Your Uploaded Files:\n" + ownFiles);
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ClassNotFoundException: " + e.getMessage());
        }
    }

    static void uploadFile() {
        try {
            File file = new File(fileName);
            FileInputStream fileIn = new FileInputStream(file);
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            int totalBytesRead = 0;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                byte[] chunkToSend;
                if (bytesRead == chunkSize) {
                    chunkToSend = buffer;
                } else {
                    chunkToSend = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunkToSend, 0, bytesRead);
                }
                out.writeObject(chunkToSend);
                out.flush();
                totalBytesRead += bytesRead;
                String acknowledgement = (String) in.readObject();
                System.out.println("Server: " + acknowledgement);
                out.writeObject("Uploaded " + totalBytesRead + " of " + fileSize + " bytes");
                out.flush();
            }
            out.writeObject("EOF");
            out.flush();
            fileIn.close();
            System.out.println("File upload completed locally.");
            String serverResponse = (String) in.readObject();
            System.out.println("Server final response: " + serverResponse);

        } catch (IOException e) {
            System.err.println("IOException during file upload: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ClassNotFoundException during file upload: " + e.getMessage());
        }
    }

    static void handleUploadedFilesOption() {

        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
        try {
            System.out.println("Enter the name of the file to upload: ");
            fileName = userInput.readLine();
            File file = new File(fileName);
            fileSize = (int) file.length();
            if (!file.exists() || file.isDirectory()) {
                System.out.println("File does not exist or is a directory. Upload aborted.");
                return;
            }
            System.out.println("File size: " + fileSize);
            System.out.println("Enter the type of the file to upload: ");
            fileType = userInput.readLine();
            out.writeObject("Uploading File: " + fileName + " and size: " + fileSize + " and type: " + fileType);
            out.flush();
            // System.out.println("Response " + (String) in.readObject());
            String message1 = (String) in.readObject();
            System.out.println("Response from server: " + message1);
            String message2 = (String) in.readObject();
            System.out.println("File upload initiated for " + message2);
            String message = (String) in.readObject();
            String message3 = (String) in.readObject();
            System.out.println("File upload progress: " + message3);
            String[] parts = message3.split(" ");
            chunkSize = Integer.parseInt(parts[1]);
            uploadFile();

        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ClassNotFoundException: " + e.getMessage());
        }
    }

    static void handleFileReq() {
        StringBuilder sb = new StringBuilder();
        System.out.println("Please Enter the file name: ");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            String str = br.readLine();
            sb.append(str + " and ");
            System.out.println("Please Enter the username of the user: ");
            str = br.readLine();
            sb.append(str);
            out.writeObject(sb);
            out.flush();
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }

    }

    static void readUnreadMessages() {
        System.out.println("Unread Messages" + "\n");
        try {
            System.out.println((String) in.readObject());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static void handleClientOption(int option) {
        switch (option) {
            case 1:
                System.out.println("You selected: Connected Client Lists");
                try {
                    out.writeObject("Capability Option: 1");
                    out.flush();

                } catch (IOException e) {
                    System.err.println("IOException: " + e.getMessage());
                }
                break;
            case 2:
                System.out.println("You selected: Looking for Own Files");
                try {
                    out.writeObject("Capability Option: 2");
                    out.flush();
                    handleOwnFiles();

                } catch (IOException e) {
                    System.err.println("IOException: " + e.getMessage());
                }
                break;
            case 3:
                System.out.println("You selected: Upload File to Server");
                try {
                    out.writeObject("Capability Option: 3");
                    out.flush();
                    handleUploadedFilesOption();

                } catch (IOException e) {
                    System.err.println("IOException: " + e.getMessage());
                }
                break;
            case 4:
                System.out.println("You selected: Download File from Server");
                break;
            case 5:
                System.out.println("You selected: Looking for Own Files");
                break;
            case 6:
                System.out.println("You selected: Looking for Private Files(Request needed)");
                handleFileReq();
                break;
            case 7:
                System.out.println("You selected: Looking for Unread Messages");
                readUnreadMessages();
                break;
            case 8:
                System.out.println("You selected: Upload & Download History");
                try {
                    out.writeObject("Capability Option: 8");
                    out.flush();
                    String logHistory = (String) in.readObject();
                    System.out.println("Upload & Download History:\n" + logHistory);
                } catch (IOException e) {
                    System.err.println("IOException: " + e.getMessage());
                } catch (ClassNotFoundException e) {
                    System.err.println("ClassNotFoundException: " + e.getMessage());
                }
                break;
            case 9:
                System.out.println("Logout");
                try {
                    out.writeObject("logout");
                    out.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            default:
                System.out.println("Invalid option. Please select a valid capability option number (1-8).");
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Please Enter your username:");
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            String username = userInput.readLine();
            Socket socket = new Socket("localhost", 6666);
            // SyPainstem.out.println("Connected to proxy server");
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("Username: " + username);
            out.flush();
            String response = (String) in.readObject();

            System.out.println("Server: " + response);
            if (response.contains("already taken")) {
                socket.close();
                return;
            } else
                System.out.println("Looking for Capabilities:....." + "\n=>yes" + "\n=>no");
            String capability = userInput.readLine();
            if (!capability.equals("yes") && !capability.equals("no")) {
                System.out.println("Invalid Capability Option. Exiting...");
                socket.close();
                return;
            } else {
                showMenu();
                int option = Integer.parseInt(userInput.readLine());
                while (option < 1 || option > 8) {
                    System.out.println("Invalid option. Please select a valid capability option number (1-8):");
                    option = Integer.parseInt(userInput.readLine());
                }
                handleClientOption(option);

            }
            // while (true) {
            // }

            while (true) {
                // System.out.println("Response from server: " + (String) in.readObject());
            }
            // socket.close();

        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ClassNotFoundException: " + e.getMessage());
        }
    }
}
