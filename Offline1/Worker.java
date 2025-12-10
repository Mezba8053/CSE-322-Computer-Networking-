package Offline1;

import java.net.Socket;
// import java.util.Date;
import java.util.*;
import java.io.*;

public class Worker extends Thread {
    Socket socket;
    List<String> clientNames;
    Map<Socket, String> Clients;
    ObjectOutputStream out;
    ObjectInputStream in;
    String name = "";
    List<String> uploadedFiles = new ArrayList<>();
    static Map<String, String> unreadMessages = new HashMap<>();
    int maxBufferSize;
    int remainingBufferSize;
    int minChunkSize;
    int maxChunkSize;
    static int reqCount = 0;
    String ROOT_DIR = "";
    String PUBLIC_DIR = "";
    String PRIVATE_DIR = "";

    public Worker(Socket socket, List<String> clientNames, Map<Socket, String> Clients) {
        this.socket = socket;
        this.clientNames = clientNames;
        this.Clients = Clients;
    }

    public void Initialization() {
        maxBufferSize = 1024 * 1024;
        minChunkSize = 10;
        maxChunkSize = 64 * 1024;
        remainingBufferSize = maxBufferSize;
        // String UPLOAD_DIR = ROOT_DIR + "uploaded/";
        PUBLIC_DIR = ROOT_DIR + "public/";
        PRIVATE_DIR = ROOT_DIR + "private/";
    }

    public void checkOnlineClients() {
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Socket, String> entry : Clients.entrySet()) {
                Socket s = entry.getKey();
                if (!s.isClosed() && s.isConnected()) {
                    sb.append(entry.getValue());
                    sb.append("\n");
                }
            }
            out.writeObject(sb.toString());
            out.flush();
        } catch (Exception e) {
            System.err.println("Error checking online clients: " + e.getMessage());
        }
    }

    public void createFolder(String folderName) throws IOException {
        File folder = new File(folderName);
        File logFile = new File(folder, "log.txt");
        ROOT_DIR = folderName + "/";
        if (!folder.exists()) {
            folder.mkdirs();
            logFile.createNewFile();
        }
    }

    public void writeOnLogFile(String logEntry) {
        try {
            File logFile = new File(ROOT_DIR + "log.txt");
            FileWriter fw = new FileWriter(logFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(logEntry);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void readLogFile() {
        try {
            File logFile = new File(ROOT_DIR + "log.txt");
            BufferedReader br = new BufferedReader(new FileReader(logFile));
            String line;
            StringBuilder logContent = new StringBuilder();
            while ((line = br.readLine()) != null) {
                logContent.append(line).append("\n");
            }
            br.close();
            out.writeObject(logContent.toString());
            out.flush();
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
        }
    }

    public void showPublicFiles() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("All Public Files\n");
        for (String str : clientNames) {
            File usrDir = new File(str + "/public/");
            if (usrDir.exists() && usrDir.isDirectory()) {
                String[] fileList = usrDir.list();
                sb.append("User: ").append(str).append("\n");
                // File publicDir = new File(str + "/public/");
                if (fileList != null && fileList.length > 0) {
                    for (String string : fileList) {
                        sb.append(" - ").append(string);
                    }
                }
                sb.append("\n");
            }
        }
        out.writeObject(sb.toString());
        out.flush();
    }

    public void handleClientsOwnFiles() {
        try {
            File userDir = new File(ROOT_DIR);
            StringBuilder result = new StringBuilder();

            if (userDir.exists() && userDir.isDirectory()) {
                // result.append("Your Files \n");
                result.append(" - Public Files:\n");
                File publicDir = new File(name + "/public/");
                String[] filesList = publicDir.list();
                if (filesList != null && filesList.length > 0) {
                    for (String fileName : filesList) {
                        result.append("  - ").append(fileName).append("\n");
                    }
                }

                result.append("\nPrivate Files:\n");
                File privateDir = new File(name + "/private/");
                filesList = privateDir.list();
                if (filesList != null && filesList.length > 0) {
                    for (String fileName : filesList) {
                        result.append("  - ").append(fileName).append("\n");
                    }
                }
            }

            out.writeObject(result.toString());
            out.flush();

        } catch (IOException e) {
            System.err.println("Error handling client's own files: " + e.getMessage());
        }
    }

    public void uploadFiles(String fileName, int fileSize, String fileType) {
        try {
            String fileFolder = ROOT_DIR + fileType + "/";
            File fileDir = new File(fileFolder);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }
            File file = new File(fileDir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            int totalReceived = 0;
            ByteArrayOutputStream memoryBuffer = new ByteArrayOutputStream();
            while (true) {
                Object obj = in.readObject();

                if (obj instanceof String && ((String) obj).equals("EOF")) {
                    break;
                }

                if (obj instanceof byte[]) {
                    byte[] chunkData = (byte[]) obj;
                    memoryBuffer.write(chunkData);
                    totalReceived += chunkData.length;
                    out.writeObject("Received chunk of size: " + chunkData.length);
                    out.flush();
                }

            }
            if (totalReceived != fileSize) {
                System.out.println("Mismatch in received data size!");
                out.writeObject("Failure on Uploading");
                out.flush();
                writeOnLogFile("Action: Upload Failed for file " + fileName + " Date: " + new Date().toString()
                        + " Action type: Upload" + " status: Failed");
                file.delete();
                return;
            } else {
                fos.write(memoryBuffer.toByteArray());
                out.writeObject("Upload Successful");
                out.flush();
                remainingBufferSize -= totalReceived;
                writeOnLogFile("Action: Upload Successful for file " + fileName + " Date: "
                        + new Date().toString() + " Action type: Upload" + " status: Success");
            }
            fos.close();

            // out.writeObject("File upload completed. Total size: " + totalReceived);
            // out.flush();
            System.out.println("File saved successfully to: " + file.getAbsolutePath());

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error uploading files: " + e.getMessage());
        }

    }

    public void handleUploadedFiles() {
        try {
            String fileInfo = (String) in.readObject();
            StringBuilder sb = new StringBuilder();
            if (fileInfo.startsWith("Uploading File: ")) {
                sb.append("Server received file info: " + fileInfo);
                String[] fileParts = fileInfo.split(" and ");
                String uploadFileName = fileParts[0].substring(16).trim(); // "Uploading File: " is 16 chars
                int uploadFileSize = Integer.parseInt(fileParts[1].substring(6).trim());
                String uploadFileType = fileParts[2].substring(6).trim();
                sb.append(uploadFileName + " " + uploadFileSize + " " + uploadFileType);
                if (remainingBufferSize < uploadFileSize) {
                    sb.append("Sorry storage has exceeded its limit. Please try again later.");
                    out.writeObject(sb.toString());
                    out.flush();
                } else {
                    // File uploadedFile = new File("uploads/" + name + "/" + uploadFileName);
                    int chunk = (int) (Math.random() * (maxChunkSize - minChunkSize + 1));
                    out.writeObject(sb.toString());
                    out.flush();
                    String fileId = uploadFileName + String.valueOf(chunk);
                    out.writeObject("chunks " + chunk +
                            " fileid " + fileId);
                    out.flush();
                    uploadFiles(uploadFileName, uploadFileSize, uploadFileType);
                }

            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error handling uploaded files: " + e.getMessage());
        }
    }

    public void handledownloadFiles() throws IOException, ClassNotFoundException {
        String fileName = (String) in.readObject();
        boolean isFound = false;
        File pub = new File(PUBLIC_DIR);
        if (pub.exists() && pub.isDirectory()) {
            String[] fileList = pub.list();
            String location = pub + fileName + "/";
            if (fileList != null && Arrays.asList(fileList).contains(fileName)) {
                isFound = true;
                downloadFiles(fileName, location);
            }
        }
        File pri = new File(PRIVATE_DIR);
        if (pri.exists() && pri.isDirectory()) {
            String[] fileList = pri.list();
            String location = pri + fileName + "/";
            if (fileList != null && Arrays.asList(fileList).contains(fileName)) {
                isFound = true;
                downloadFiles(fileName, location);
            }
        }
        if (!isFound) {
            out.writeObject("Sorry File not found!");
            out.flush();
        }

    }

    public void downloadFiles(String fileName, String location) throws IOException {
        File download = new File(location);
        FileInputStream fis = new FileInputStream(download);
        byte[] buffer = new byte[maxChunkSize];
        int bytesRead;
        int totalBytesRead = 0;
        while ((bytesRead = fis.read(buffer)) != -1) {
            byte[] chunkToSend;
            if (bytesRead == maxChunkSize) {
                chunkToSend = buffer;
            } else {
                chunkToSend = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkToSend, 0, bytesRead);
            }
            out.writeObject(chunkToSend);
            out.flush();
            totalBytesRead += bytesRead;

        }
        out.writeObject("EOF");
        out.flush();
        fis.close();
        out.writeObject("Download Successful");
        out.flush();
        writeOnLogFile("Action: Download Successful for file " + fileName + " Date: "
                + new Date().toString() + " Action type: Download" + " status: Success");
    }

    public void logOut() throws IOException {

        out.writeObject("User '" + name + "' logged out.");
        out.flush();
        Clients.remove(socket);
        try {
            socket.close();
        } catch (IOException ignore) {
        }

    }

    public void sendMessageToAll(String reqFile, String reqId) throws IOException {
        for (String reqName : clientNames) {
            if (!reqName.equals(name)) {
                String message = name + " wants this " + reqFile + " file.";
                if (!unreadMessages.containsKey(reqName)) {
                    unreadMessages.put(reqName, message);
                } else {
                    String prev = unreadMessages.get(reqName);
                    unreadMessages.put(reqName, prev + "\n" + message);
                }

            }

        }
        out.writeObject("Request has been sent.Request Id :" + reqId);
        out.flush();

    }

    public void sendMessageToSpecific(String reqFile, String reqClient, String reqId) throws IOException {
        boolean found = false;
        for (String reqName : clientNames) {
            if (reqName.equals(reqClient)) {
                String message = name + " wants this " + reqFile + " file.";
                if (!unreadMessages.containsKey(reqName)) {
                    unreadMessages.put(reqName, message);
                } else {
                    String prev = unreadMessages.get(reqName);
                    unreadMessages.put(reqName, prev + "\n" + message);
                }
                found = true;
            }

        }
        if (!found) {
            out.writeObject("Sorry Wrong Client Name -_-");
            out.flush();
        } else {
            out.writeObject("Request has been sent.Request Id :" + reqId);
            out.flush();

            found = false;
        }
    }

    public void handleFileRequests() {
        try {
            String reqMessage = (String) in.readObject();
            String[] parts = reqMessage.split(" and ");
            String fileReqName = parts[0].trim();
            String clientName = parts[1].trim();
            String reqId = name + String.valueOf(reqCount++);
            if (clientName.equals("ALL")) {
                sendMessageToAll(fileReqName, reqId);

            } else {
                sendMessageToSpecific(fileReqName, clientName, reqId);
            }

        } catch (IOException e) {
            System.err.println("Error handling file requests: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Error handling file requests: " + e.getMessage());
        }
    }

    public void showUnreadMessage() throws IOException {
        if (unreadMessages.containsKey(name)) {
            out.writeObject(unreadMessages.get(name));
            out.flush();
        } else {
            out.writeObject("No new Message to show");
            out.flush();
        }
        unreadMessages.remove(name);
    }

    public void handleOption(int option) throws IOException, ClassNotFoundException {
        switch (option) {
            case 1:
                checkOnlineClients();
                break;
            case 2:
                handleClientsOwnFiles();
                break;
            case 3:
                handleUploadedFiles();
                break;
            case 4:
                handledownloadFiles();
                break;
            case 5:
                showPublicFiles();
                break;
            case 6:
                handleFileRequests();
                break;
            case 7:
                showUnreadMessage();
                break;
            case 8:
                readLogFile();
                break;
            case 9:
                logOut();
                throw new IOException("User logged out");
            default:
                out.writeObject("Invalid option selected.");
                out.flush();
                break;
        }
    }

    public void run() {
        try {
            out = new ObjectOutputStream(this.socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(this.socket.getInputStream());
            String userName = (String) in.readObject();
            if (userName.startsWith("Username: ")) {
                name = userName.substring(10).trim();
                if (!clientNames.contains(name)) {
                    clientNames.add(name);
                    out.writeObject("Welcome, " + name + "!");
                    out.flush();
                    Initialization();
                    createFolder(name);
                    Clients.put(socket, name);
                    System.out.println("User '" + name + "' connected.");
                    // break;
                } else {
                    out.writeObject("Username already taken. Please try another one.");
                    out.flush();
                    socket.close();
                    return;
                }

            }
            String capability = (String) in.readObject();
            // int option=(int) in.readObject();
            if (capability.startsWith("Capability Option: ")) {
                int option = Integer.parseInt(capability.substring(19).trim());
                System.out.println("User " + name + " selected option: " + option);
                out.writeObject("Server : Option " + option + " received.");
                out.flush();
                handleOption(option);
            }

            while (true) {
                String capa = (String) in.readObject();

                if (capa.startsWith("Capability Option: ")) {
                    int option = Integer.parseInt(capa.substring(19).trim());
                    System.out.println("User " + name + " selected option: " + option);
                    out.writeObject("Server : Option " + option + " received.");
                    out.flush();
                    handleOption(option);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                Clients.remove(socket);
                socket.close();
            } catch (IOException ignore) {
                System.out.println("Cleanup complete for user '" + name + "'.");

            }
        }
    }
}
