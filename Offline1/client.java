package Offline1;

import java.net.*;
import java.util.ArrayList;
import java.util.*;
import java.io.*;

public class client {
    static ObjectOutputStream out;
    static ObjectInputStream in;
    static String fileName = "";
    static int fileSize = 0;
    static String fileType = "";
    static int chunkSize = 0;
    static String userName = "";
    static String requestId = "";
    static String reqClientName = "";
    static boolean isReq = false;

    static void showMenu() {
        System.out.println("Capability List: ");
        System.out.println("1. Show Connected Client Lists:");
        System.out.println("2. Looking for Uploaded Files:");
        System.out.println("3. Upload File to Server:");
        System.out.println("4. Download File from Server:");
        System.out.println("5. Looking for Public Files:");
        System.out.println("6. Requesting for a File(Request needed):");
        System.out.println("7. Looking for Unread Messages:");
        System.out.println("8. Upload & Download History:");
        System.out.println("9. Send Message");
        System.out.println("10. Exit");
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

    static void showPublicFiles() throws IOException, ClassNotFoundException {
        String ownFiles = (String) in.readObject();
        System.out.println(ownFiles);
    }

    static void sendMessage() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Enter the recipient's username: ");
            String recipient = br.readLine();
            out.writeObject(recipient);
            out.flush();
            String serverPrompt = (String) in.readObject();
            System.out.println(serverPrompt);
            if (serverPrompt.contains("Sorry Wrong Client Name")) {
                System.out.println("Message not sent.");
                return;
            } else {
                System.out.println("Recipient found: " + serverPrompt);
            }
            System.out.println("Enter your message: ");
            String message = br.readLine();
            StringBuilder sb = new StringBuilder();
            sb.append("To: " + recipient + "\nMessage: " + message);
            String fullMessage = sb.toString();
            out.writeObject(fullMessage);
            out.flush();
            String serverResponse = (String) in.readObject();
            System.out.println("Server: " + serverResponse);
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

            }
            out.writeObject("EOF");
            out.flush();
            // out.writeObject("Uploaded " + totalBytesRead + " of " + fileSize + " bytes");
            // out.flush();
            isReq = false;
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
            if (!isReq) {
                System.out.println("Enter the name of the file to upload: ");
                fileName = userInput.readLine();
            }

            System.out.println("Processing file upload...");
            File file = new File(fileName);
            fileSize = (int) file.length();
            if (!file.exists() || file.isDirectory()) {
                out.writeObject("Abort");
                out.flush();
                System.out.println("File does not exist or is a directory. Upload aborted.");
                return;
            }
            if (!isReq) {
                System.out.println("File size: " + fileSize);
                System.out.println("Enter the type of the file to upload: ");
                fileType = userInput.readLine();
                if (fileType.isEmpty()) {
                    fileType = "public";
                }
            }
            if (isReq) {
                fileType = "public";
            }
            if (!isReq) {
                out.writeObject("Uploading File: " + fileName + " and size: " + fileSize + " and type: " + fileType);
                out.flush();
            }
            if (isReq) {
                out.writeObject("Uploading File: " + fileName + " and size: " + fileSize + " and type: " + fileType
                        + " and requestId:" + requestId + " and reqClient:" + reqClientName);
                out.flush();
                System.out
                        .println("Request ID sent: " + requestId + " fileType: " + fileType + " fileName: " + fileName);

            }
            String message1 = (String) in.readObject();
            System.out.println("Response from server: \n" + message1);
            String message2 = (String) in.readObject();
            System.out.println("File upload progress: " + message2);
            String[] parts = message2.split(" ");
            chunkSize = Integer.parseInt(parts[1]);
            uploadFile();

        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ClassNotFoundException: " + e.getMessage());
        }
    }

    static void handleDownload() throws IOException, ClassNotFoundException {
        BufferedReader br = new BufferedReader(new InputStreamReader((System.in)));
        System.out.println("Enter the files to download: ");
        fileName = br.readLine();
        out.writeObject(fileName);
        out.flush();
        System.out.println("Enter the file type: ");
        fileType = " ";
        fileType = br.readLine();
        out.writeObject(fileType);
        out.flush();

        Thread downloadThread = new Thread(() -> {
            try {
                downloadFiles();
            } catch (Exception e) {
                System.err.println("Download thread error: " + e.getMessage());
            }
        });
        downloadThread.start();
        try {
            downloadThread.join();
        } catch (InterruptedException e) {
            System.err.println("Download interrupted: " + e.getMessage());
        }
    }

    static void downloadFiles() throws ClassNotFoundException, IOException {
        File fi = new File("downloads/" + userName);
        if (!fi.exists()) {
            fi.mkdirs();
        }
        File download = new File(fi, fileName);
        File parentDir = download.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        ByteArrayOutputStream memoryBuffer = new ByteArrayOutputStream();
        while (true) {
            Object obj = in.readObject();

            if (obj instanceof String && ((String) obj).equals("EOF")) {
                break;
            }

            if (obj instanceof byte[]) {
                byte[] chunkData = (byte[]) obj;
                memoryBuffer.write(chunkData);
            }
        }
        String response = (String) in.readObject();
        System.out.println("Server: " + response);
        if (memoryBuffer.size() > 0) {
            FileOutputStream fos = new FileOutputStream(download);
            fos.write(memoryBuffer.toByteArray());
            fos.close();
            System.out.println("File downloaded successfully to: " + download.getAbsolutePath());
            System.out.println("File size: " + memoryBuffer.size() + " bytes");
        } else {
            System.out.println("Download failed: No data received (file not found or empty).");
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
            out.writeObject(sb.toString());
            out.flush();
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }

    }

    static void readUnreadMessages() {
        System.out.println("Unread Messages" + "\n");
        try {
            String resp = (String) in.readObject();
            System.out.println(resp);
            if (resp.contains(" wants ")) {
                String[] messages = resp.split("\n");
                for (String message : messages) {
                    if (message.contains(" wants ")) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                        System.out.println("Do you give permission: yes or no?");
                        String ans = br.readLine();
                        if (ans.equals("yes")) {
                            out.writeObject("permission granted ");
                            out.flush();
                            System.out.println("Processing permission...");
                            reqClientName = message.split(" wants ")[0].trim();
                            String[] part = message.split(" requestID:");
                            if (part.length == 2) {
                                requestId = part[1].trim();
                                isReq = true;
                                System.out.println("Request ID extracted: " + requestId);
                                handleUploadedFilesOption();

                            } else {
                                System.out.println("Could not extract request ID. Aborting.");
                            }

                        } else {
                            out.writeObject("permission denied ");
                            out.flush();
                        }
                    }
                }
            }
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }

    static void showClients() throws IOException, ClassNotFoundException {
        System.out.println((String) in.readObject());

    }

    static void handleClientOption(int option) throws ClassNotFoundException, IOException, InterruptedException {
        out.writeObject("Capability Option: " + option);
        out.flush();
        String serverAck = (String) in.readObject();
        System.out.println(serverAck);
        switch (option) {
            case 1:
                System.out.println("You selected: Connected Client Lists");
                showClients();
                break;
            case 2:
                System.out.println("You selected: Looking for Own Files");
                handleOwnFiles();
                break;
            case 3:
                System.out.println("You selected: Upload File to Server");
                handleUploadedFilesOption();
                break;
            case 4:
                System.out.println("You selected: Download File from Server");
                handleDownload();
                break;
            case 5:
                System.out.println("You selected: Looking for Other's Public Files");
                showPublicFiles();
                break;
            case 6:
                System.out.println("You selected: Looking for Private Files(Request needed)");
                handleFileReq();
                String reqResponse = (String) in.readObject();
                System.out.println(reqResponse);
                break;
            case 7:
                System.out.println("You selected: Looking for Unread Messages");
                readUnreadMessages();
                break;
            case 8:
                System.out.println("You selected: Upload & Download History");
                String logHistory = (String) in.readObject();
                System.out.println("Upload & Download History:\n" + logHistory);
                break;
            case 9:
                System.out.println("You selected: Send Message");
                sendMessage();
                break;
            case 10:
                System.out.println("Logout");
                String logoutMsg = (String) in.readObject();
                System.out.println(logoutMsg);
                System.exit(0);
                break;
            default:
                System.out.println("Invalid option.");
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Please Enter your username:");
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            String username = userInput.readLine();
            Socket socket = new Socket("localhost", 6666);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            out.writeObject("Username: " + username);
            out.flush();
            userName = username;
            String response = (String) in.readObject();

            System.out.println("Server: " + response);
            if (response.contains("already taken")) {
                socket.close();
                return;
            } else
                System.out.println("Looking for Capabilities:....." + "\n=>yes" + "\n=>no");
            String capability = userInput.readLine();
            if (!(capability.equals("yes") || capability.equals("y")) && !capability.equals("no")) {
                System.out.println("Invalid Capability Option. Exiting...");
                socket.close();
                return;
            }
            while (true) {
                showMenu();
                int option;
                try {
                    option = Integer.parseInt(userInput.readLine());
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a number between 1-9.");
                    continue;
                }

                if (option < 1 || option > 10) {
                    System.out.println("Invalid option. Exiting...");
                    break;
                }

                if (option == 10) {
                    handleClientOption(option);
                    break;
                }
                handleClientOption(option);
                System.out.println("\n--- Press Enter to continue ---");
                userInput.readLine();
            }

            while (true) {
                // System.out.println("Response from server: " + (String) in.readObject());
            }
            // socket.close();

        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ClassNotFoundException: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("InterruptedException: " + e.getMessage());
        }
    }
}
