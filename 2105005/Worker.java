import java.net.Socket;
// import java.util.Date;
import java.util.*;
import java.io.*;

public class Worker extends Thread {
    Socket socket;
    List<String> clientNames;
    List<String> offlineClients;
    Map<Socket, String> Clients;
    static Map<String, List<String>> fileRequests = new HashMap<>();
    ObjectOutputStream out;
    ObjectInputStream in;
    String name = "";
    List<String> uploadedFiles = new ArrayList<>();
    static Map<String, String> unreadMessages = new HashMap<>();
    long maxBufferSize;
    long remainingBufferSize;
    int minChunkSize;
    int maxChunkSize;
    static int reqCount = 0;
    String ROOT_DIR = "";
    String PUBLIC_DIR = "";
    String PRIVATE_DIR = "";

    public Worker(Socket socket, List<String> clientNames, Map<Socket, String> Clients, List<String> offlineClients) {
        this.offlineClients = offlineClients;
        this.socket = socket;
        this.clientNames = clientNames;
        this.Clients = Clients;
    }

    public void Initialization() {
        maxBufferSize = 1024L * 1024L * 1024L * 1024L;
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
            if (offlineClients.size() > 0) {
                sb.append("Offline Clients:\n");
                for (String offClient : offlineClients) {
                    sb.append(offClient + " (offline)");
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

            while (true) {
                Object obj = in.readObject();

                if (obj instanceof String && ((String) obj).equals("EOF")) {
                    break;
                }

                if (obj instanceof Integer) {
                    int chunkSize = (Integer) obj;
                    byte[] chunkData = new byte[chunkSize];
                    in.readFully(chunkData);
                    fos.write(chunkData);
                    totalReceived += chunkData.length;
                    out.writeObject("Received chunk of size: " + chunkData.length);
                    out.flush();
                }
            }

            fos.close();

            if (totalReceived != fileSize) {
                System.out.println("Mismatch in received data size!");
                out.writeObject("Failure on Uploading");
                out.flush();
                writeOnLogFile("Action: Upload Failed for file " + fileName + " Date: " + new Date().toString()

                        + " Action type: Upload" + " status: Failed");
                file.delete();
                return;
            } else {
                out.writeObject("Upload Successful");
                out.flush();
                writeOnLogFile("Action: Upload Successful for file " + fileName + " Date: "

                        + new Date().toString() + " Action type: Upload" + " status: Success");

                remainingBufferSize -= totalReceived;
            }

            System.out.println("File saved successfully to: " + file.getAbsolutePath());

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error uploading files: " + e.getMessage());
        }
    }

    public void handleUploadedFiles() {
        try {
            String reqId = "";
            String reqClientName = "";
            List<String> reqDetails = null;
            String fileInfo = (String) in.readObject();
            if (fileInfo.equals("Abort")) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            if (fileInfo.startsWith("Uploading File: ")) {
                sb.append("Server received file info: " + fileInfo);
                String[] fileParts = fileInfo.split(" and ");
                String uploadFileName = fileParts[0].substring(16).trim();
                int uploadFileSize = Integer.parseInt(fileParts[1].substring(6).trim());
                String uploadFileType = fileParts[2].substring(6).trim();
                System.out.println("Receiving file: " + uploadFileName + " of size: " +
                        uploadFileSize + " and type: "
                        + uploadFileType);
                // System.out.println("All fileRequests keys: " + fileRequests.keySet());
                // System.out.println("Looking for reqId: " + reqId);
                if (fileInfo.contains("requestId:")) {
                    reqId = fileParts[3].substring(10).trim();
                    reqClientName = fileParts[4].substring(10).trim();
                    System.out.println("Request ID received: " + reqId + " for file: " +
                            uploadFileName);
                    if (fileRequests.containsKey(reqId)) {
                        reqDetails = fileRequests.get(reqId);
                        String reqClient = reqDetails.get(0);
                        String reqFile = reqDetails.get(1);
                        System.out.println("Request details found: Client - " + reqClient + ", File - " + reqFile);
                        if (!reqFile.equals(uploadFileName)) {
                            out.writeObject("Upload aborted. Request ID does not match the file being uploaded.");
                            out.flush();
                            return;
                        } else {

                            fileRequests.remove(reqId, Arrays.asList(reqClient, reqFile));
                        }
                    }
                } else {
                    sb.append(uploadFileName + " " + uploadFileSize + " " + uploadFileType);
                }
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
                    Thread uploadThread = new Thread(() -> {
                        uploadFiles(uploadFileName, uploadFileSize, uploadFileType);
                    });
                    uploadThread.start();
                    uploadThread.join();
                    // uploadFiles(uploadFileName, uploadFileSize, uploadFileType);
                    if (reqId != null && reqDetails != null) {
                        String requester = reqDetails.get(0);
                        String notif = "Client " + name + " uploaded the file '" + uploadFileName
                                + "' for your request ID '" + reqId + "'.";

                        if (unreadMessages.containsKey(reqClientName)) {
                            unreadMessages.put(reqClientName, unreadMessages.get(reqClientName) + "\n" + notif);
                        } else {
                            unreadMessages.put(reqClientName, notif);

                        }
                    }
                }

            }
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            System.err.println("Error handling uploaded files: " + e.getMessage());
        }
    }

    public void handledownloadFiles() throws IOException, ClassNotFoundException, InterruptedException {
        String fileName = (String) in.readObject();
        boolean isFound = false;
        String fileType = (String) in.readObject();
        File pub = new File(ROOT_DIR + "/public/" + fileName);
        if (pub.exists() && pub.isFile() && (fileType.equals(" ") || fileType.equals("public"))) {
            isFound = true;
            String location = pub + fileName;
            // downloadFiles(fileName, pub.getAbsolutePath());
            Thread downloadThread = new Thread(() -> {
                try {
                    downloadFiles(fileName, pub.getAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Error in download thread: " + e.getMessage());
                }
            });
            downloadThread.start();
            downloadThread.join();

        }
        if (!isFound) {
            File pri = new File(ROOT_DIR + "/private/" + fileName);
            if (pri.exists() && pri.isFile() && (fileType.equals(" ") || fileType.equals("private"))) {
                isFound = true;
                // downloadFiles(fileName, pri.getAbsolutePath());
                Thread downloadThread = new Thread(() -> {
                    try {
                        downloadFiles(fileName, pri.getAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("Error in download thread: " + e.getMessage());
                    }
                });
                downloadThread.start();
                downloadThread.join();
            }
        }
        if (!isFound) {
            out.writeObject("EOF");
            out.flush();
            out.writeObject("Sorry File not found!");
            out.flush();
        }

    }

    public void downloadFiles(String fileName, String location) throws IOException {
        File download = new File(location);
        if (!download.exists() || !download.isFile()) {
            out.writeObject("EOF");
            out.flush();
            out.writeObject("File does not exist or is not a file.");
            out.flush();
            return;
        }
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
            out.writeObject(chunkToSend.length);
            out.write(chunkToSend);
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
        offlineClients.add(name);
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
                String message = name + " wants " + reqFile + " file. requestID:" + reqId;
                if (!unreadMessages.containsKey(reqName)) {
                    unreadMessages.put(reqName, message);
                } else {
                    String prev = unreadMessages.get(reqName);
                    unreadMessages.put(reqName, prev + "\n" + message);
                }
                fileRequests.put(reqId, Arrays.asList(reqName, reqFile));

            }

        }
        out.writeObject("Request has been sent.Request Id :" + reqId);
        out.flush();

    }

    public void sendMessageToSpecific(String reqFile, String reqClient, String reqId) throws IOException {
        boolean found = false;
        for (String reqName : clientNames) {
            if (reqName.equals(reqClient)) {
                String message = name + " wants " + reqFile + " file. requestID:" + reqId;
                if (!unreadMessages.containsKey(reqName)) {
                    unreadMessages.put(reqName, message);
                } else {
                    String prev = unreadMessages.get(reqName);
                    unreadMessages.put(reqName, prev + "\n" + message);
                }
                fileRequests.put(reqId, Arrays.asList(reqName, reqFile));
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

    public void showUnreadMessage() throws IOException, ClassNotFoundException {
        if (unreadMessages.containsKey(name)) {
            out.writeObject(unreadMessages.get(name));
            out.flush();
            String res = (String) in.readObject();
            if (res.contains("permission granted ")) {
                res = res.substring(1);
                String[] parts = res.split(" ");
                String fileReq = parts[0].trim();
                String fiName = parts[1].trim();

                handleUploadedFiles();

            }
        } else {
            out.writeObject("No new Message to show");
            out.flush();
        }
        unreadMessages.remove(name);
    }

    public void sendMessage() {
        try {
            String recipient = (String) in.readObject();
            if (!clientNames.contains(recipient) && !recipient.equals("ALL")) {
                out.writeObject("Sorry Wrong Client Name -_-");
                out.flush();
                return;
            }
            if (recipient.equals("ALL")) {
                out.writeObject("Enter your message to send to all clients: ");
                out.flush();
                String message = (String) in.readObject();
                for (String clientName : clientNames) {
                    if (!clientName.equals(name)) {
                        if (!unreadMessages.containsKey(clientName)) {
                            unreadMessages.put(clientName, "Message from " + name + ": " + message);
                        } else {
                            String prev = unreadMessages.get(clientName);
                            unreadMessages.put(clientName, prev + "\nMessage from " + name + ": " + message);
                        }
                    }
                }
            } else {
                out.writeObject("Enter your message to send to " + recipient + ": ");
                out.flush();
                String message = (String) in.readObject();
                if (!unreadMessages.containsKey(recipient)) {
                    unreadMessages.put(recipient, "Message from " + name + ": " + message);
                } else {
                    String prev = unreadMessages.get(recipient);
                    unreadMessages.put(recipient, prev + "\nMessage from " + name + ": " + message);
                }
            }

            out.writeObject("Message sent to the client.");
            out.flush();

        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    public void handleOption(int option) throws IOException, ClassNotFoundException, InterruptedException {
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
                sendMessage();
                break;
            case 10:
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
                synchronized (clientNames) {
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
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
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
