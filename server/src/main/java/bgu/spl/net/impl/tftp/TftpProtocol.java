package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import java.nio.file.Files;



class FilesRepository {
    static ConcurrentHashMap<String, byte[]> files = new ConcurrentHashMap<>();
}

class ActiveUsers {
    static ConcurrentHashMap<Integer, String> users = new ConcurrentHashMap<>();
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private int connectionId;
    private ConnectionsImpl<byte[]> connections;
    private TftpEncoderDecoder encoderDecoder = new TftpEncoderDecoder();
    private boolean shouldTerminate = false;
    private String currFile = "";
    private short currentBlock = 0;
    private int currentPositionRRQ = 0;
    private int currentPositionDIRQ = 0;
    private short currentOpcode = -1;
    private boolean isReadRequestComplete = true;
    private boolean isDirectoryRequestComplete = true;
    private short directoryRequestBlockNumber = 0;
    private short receivedBlockNumber;
    private String filesDir = "Files" + File.separator;
    //private String filesDir = "server" + File.separator + "Files" + File.separator;
    private FileOutputStream fos = null;

    @Override
    public void start(int connectionId, ConnectionsImpl<byte[]> connections) {
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        initializeFileRepository();
    }

    @Override
    public void process(byte[] message) {
        currentOpcode = (short) (((short) (message[0] & 0xff) << 8) | (short) (message[1] & 0xff));
        if(!ActiveUsers.users.containsKey(connectionId) & currentOpcode != 7){
            System.out.println("User not logged in");
            byte[] errorPacket = createErrorPacket(6, "User not logged in");
            connections.send(connectionId, encoderDecoder.encode(errorPacket));
            return;
        }
        switch (currentOpcode) 
        {
            case 1:{ //RRQ
                try{
                    byte[] fileName = Arrays.copyOfRange(message,2, message.length - 1);
                    currFile = new String(fileName, StandardCharsets.UTF_8);
                    
                    
                    if (!FilesRepository.files.containsKey(currFile)) {
                        // File not found, send error packet
                        byte[] errorPacket = createErrorPacket(1, "File not found");
                        connections.send(connectionId, encoderDecoder.encode(errorPacket));
                        break;
                    }
                    else{
                        currentBlock = 1;
                        isReadRequestComplete = false;
                        byte[] dataPacket = createDataPacketRRQ(currFile);
                        connections.send(connectionId, encoderDecoder.encode(dataPacket));
                        break; 
                    }   
                }
                catch (Exception e) {
                    e.printStackTrace();
                    byte[] errorPacket = createErrorPacket(2, "File cannot be written, read or deleted");
                    connections.send(connectionId, encoderDecoder.encode(errorPacket));
                }
                break;
            }
            case 2:{ // WRQ
                currFile = new String(Arrays.copyOfRange(message, 2, message.length - 1), StandardCharsets.UTF_8);
                System.out.println("Files in repository: " + FilesRepository.files.keySet());
                System.out.println("trimmedCurrFile: " + currFile);
                try {
                    // Prevent overwriting an existing file
                    if (FilesRepository.files.containsKey(currFile)) {
                        // Send an error packet if the file already exists
                        byte[] errorPacket = createErrorPacket(5, "File already exists");
                        connections.send(connectionId, errorPacket);
                    } else {
                        // Create an entry for the new file
                        FilesRepository.files.put(currFile, new byte[0]);
                        // Create and send an ACK packet for the WRQ
                        byte[] ackPacket = createAckPacket((short) 0);
                        connections.send(connectionId, ackPacket);
                        System.out.println("ackPacket sent");
                    }
                } catch (Exception e) {
                    // Handle exceptions by sending an error packet
                    byte[] errorPacket = createErrorPacket(0, "Error processing WRQ");
                    connections.send(connectionId, encoderDecoder.encode(errorPacket));
                }
                break;
            }
            case 3: { //DATA
                receivedBlockNumber = (short) (((message[4] & 0xff) << 8) | (message[5] & 0xff));
                byte[] data = Arrays.copyOfRange(message, 6, message.length);
                // Check if there's enough space on disk
                File fileToWrite = new File(filesDir + currFile);
                long usableSpace = new File("/").getUsableSpace();
                if (data.length >= usableSpace) {
                    // Not enough space, send error packet
                    byte[] errorPacket = createErrorPacket(3, "Disk full or allocation exceeded – No room in disk");
                    connections.send(connectionId, encoderDecoder.encode(errorPacket));
                    resetState();
                    if (fileToWrite.exists()) {
                        fileToWrite.delete(); // Delete partially written file
                    }
                    break;
                } else {
                    // Enough space, proceed with handling the data
                    byte[] ackPacket = createAckPacket(receivedBlockNumber);
                    connections.send(connectionId, encoderDecoder.encode(ackPacket));
                    byte[] oldData = FilesRepository.files.getOrDefault(currFile, new byte[0]);
                    byte[] newData = new byte[oldData.length + data.length];
                    System.arraycopy(oldData, 0, newData, 0, oldData.length);
                    System.arraycopy(data, 0, newData, oldData.length, data.length);
                    FilesRepository.files.put(currFile, newData);
                    
                    // If data packet is the last one (less than 512 bytes), send broadcast message
                    if (data.length < 512) {
                        // Create and send broadcast packet indicating the file has been added
                        byte[] bcastPacket = createBcastPacket((byte) 1, currFile.getBytes(StandardCharsets.UTF_8)); // 1 for file addition
                        for (Integer id : ActiveUsers.users.keySet()) {
                            connections.send(id, bcastPacket);
                        }
                        try {
                            fileToWrite.createNewFile();
                            fos = new FileOutputStream(fileToWrite);
                            fos.write(newData);
                            fos.flush();
                            fos.close();
                            fos = null;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                        resetState();
                    }
                    break;
                }
            }
            
            
            case 4:{ // ACK
                receivedBlockNumber = (short) (((short) message[2]) << 8 | (short) (message[3]) & 0X00FF);
                System.out.println("received ack " + receivedBlockNumber);
                if(receivedBlockNumber!=0){
                    if (receivedBlockNumber == currentBlock) {
                        if (isReadRequestComplete) {
                            resetState();
                        }
                        else {
                            currentBlock ++;
                            byte[] dataPacket = createDataPacketRRQ(currFile);
                            connections.send(connectionId, encoderDecoder.encode(dataPacket));
                        }
                        break;
                    }
                    else if (receivedBlockNumber == directoryRequestBlockNumber) {
                        if (isDirectoryRequestComplete == false) {
                            directoryRequestBlockNumber++;
                            byte[] dataPacket = createDataPacketDIRQ(currFile);
                            connections.send(connectionId, encoderDecoder.encode(dataPacket));
                            break;
                        }
                        else {
                            resetState();
                            break;
                        }
                    }
                    else {
                        byte[] errorPacket = createErrorPacket(0, "Wrong block number");
                        connections.send(connectionId, encoderDecoder.encode(errorPacket));
                        break;
                    }
                }
            }
            case 5:{ // ERROR
                short ErrorCodeShort = (short) (((short) message[2]) << 8 | (short) (message[3]));
                String ErrMsgStr = new String(Arrays.copyOfRange(message, 2, message.length-1), StandardCharsets.UTF_8);
                byte[] errorPacket = createErrorPacket(ErrorCodeShort,ErrMsgStr);
                connections.send(connectionId, encoderDecoder.encode(errorPacket));
                resetState();
                break;
            }
            case 6:{ // DIRQ
                String files = "";
                for (String file : FilesRepository.files.keySet()) {
                    files += file + '\0';
                }
                if(files.length()==0){
                    byte[] errorPacket = createErrorPacket(0, "Wrong block number");
                    connections.send(connectionId, encoderDecoder.encode(errorPacket));
                    break;
                }
                files = files.substring(0, files.length() - 1);
                directoryRequestBlockNumber = 1;
                isDirectoryRequestComplete = false;
                byte[] dataPacket = createDataPacketDIRQ(files);
                connections.send(connectionId, dataPacket);
                break;
            }
            case 7:{ // LOGRQ
                System.out.println("logrq");
                if (!ActiveUsers.users.containsKey(connectionId)) {
                    String username = new String(Arrays.copyOfRange(message, 2, message.length-1), StandardCharsets.UTF_8);
                    ActiveUsers.users.put(this.connectionId, username);
                    byte[] ackPacket = createAckPacket((short)(0));//send ack message
                    this.connections.send(this.connectionId, encoderDecoder.encode(ackPacket));
                    break;
                }
                else {
                    byte[] errorPacket = createErrorPacket(7, "User already logged in");
                    connections.send(connectionId, encoderDecoder.encode(errorPacket));
                }
                break;
            }
            case 8: { // DELRQ
                currFile = new String(Arrays.copyOfRange(message, 2, message.length-1), StandardCharsets.UTF_8).trim();
                File fileToDelete = new File(filesDir, currFile);
            
                if (FilesRepository.files.containsKey(currFile)) {
                    if (fileToDelete.exists() && fileToDelete.delete()) {
                        System.out.println("removed: " +FilesRepository.files.remove(currFile));
                        byte[] ackPacket = createAckPacket((short)0);
                        this.connections.send(this.connectionId, encoderDecoder.encode(ackPacket));
                        // Broadcast message indicating file deletion
                        byte[] bcastPacket = createBcastPacket((byte)0, currFile.getBytes(StandardCharsets.UTF_8)); // 0 for deletion
                        for (Integer id : ActiveUsers.users.keySet()) {
                            connections.send(id, bcastPacket);
                        }
                    } else {
                        // File exists in the repository but could not be deleted or does not exist on disk
                        byte[] errorPacket = createErrorPacket(1, "File could not be deleted or does not exist on disk");
                        connections.send(this.connectionId, encoderDecoder.encode(errorPacket));
                    }
                } else {
                    // File does not exist in the repository
                    byte[] errorPacket = createErrorPacket(1, "File not found");
                    connections.send(this.connectionId, encoderDecoder.encode(errorPacket));
                }
                resetState();
                break;
            }
            
            case 10:{ // DISC
                byte[] ackPacket = createAckPacket((short) 0);
                connections.send(connectionId, ackPacket);
                shouldTerminate = true;
                ActiveUsers.users.remove(connectionId);
                this.connections.disconnect(connectionId);
                break;
            }
            default:{
                resetState();
                byte[] errorPacket = createErrorPacket(4, "Unknown Opcode");
                connections.send(this.connectionId, encoderDecoder.encode(errorPacket));
            }
        }
    }

    private byte[] createErrorPacket(int errorCode, String errorMessage) {
        // Convert the error message into bytes, and add 1 for the terminating zero byte
        byte[] messageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        byte[] errorPacket = new byte[4 + messageBytes.length + 1];

        errorPacket[0] = (byte) (5 >> 8);
        errorPacket[1] = (byte) (5 & 0xff);

        // Set the error code
        errorPacket[2] = (byte) (errorCode >> 8);
        errorPacket[3] = (byte) (errorCode & 0xFF);

        // Copy the error message into the packet
        System.arraycopy(messageBytes, 0, errorPacket, 4, messageBytes.length);

        errorPacket[errorPacket.length - 1] = (byte) (0);

        return errorPacket;
    }
    public byte[] createDataPacketRRQ(String file) {
        byte[] newPacketData = FilesRepository.files.get(file);
    
        // Check if the data for the file is null (i.e., file not found in repository)
        if (newPacketData == null) {
            System.err.println("Error: Data for file '" + file + "' not found in repository.");
            // Handle this error appropriately. For example, you could return an error packet.
            return createErrorPacket(0, "File not found"); // Adjust the error code and message as needed.
        }
    
        byte[] dataOpcode = new byte[] {(byte) ((short)(3) >> 8), (byte) ((short)(3) & 0xff)};
        short packetLength = 512; // Default packet size for data packets
    
        // Check if this packet is the last packet
        if (currentPositionRRQ + 512 > newPacketData.length) {
            packetLength = (short) (newPacketData.length - currentPositionRRQ);
            isReadRequestComplete = true;
        }
    
        // Prepare the data packet
        byte[] dataBlock = new byte[6 + packetLength]; // Opcode + Block Number + Data
        System.arraycopy(dataOpcode, 0, dataBlock, 0, dataOpcode.length);
        dataBlock[2] = (byte) (packetLength >> 8);
        dataBlock[3] = (byte) (packetLength & 0xFF);
        dataBlock[4] = (byte) (currentBlock >> 8);
        dataBlock[5] = (byte) (currentBlock & 0xFF);
        if (packetLength > 0) {
            System.arraycopy(newPacketData, currentPositionRRQ, dataBlock, 6, packetLength);
        }

    
        // Update the current position and block number for the next packet
        currentPositionRRQ += packetLength;

        return dataBlock;
    }

    public byte[] createDataPacketDIRQ (String files) {
        byte[] dataOpcode = new byte[2];
        dataOpcode[0]=0;
        dataOpcode[1]=3;
        short newPacketlength = 0;
        if (currentPositionDIRQ + 512 < files.length()) {
            newPacketlength = 512;
            currentPositionDIRQ += 512;
        }
        else {
            newPacketlength = (short) (files.length() - currentPositionDIRQ);
            currentPositionDIRQ = 0;
            isDirectoryRequestComplete = true;            
        }
        byte[] fileNames = files.getBytes(StandardCharsets.UTF_8);
        byte[] blockNum = new byte[] {(byte) (directoryRequestBlockNumber >> 8), (byte) (directoryRequestBlockNumber & 0xff)};
        byte[] packetSize = new byte[] {(byte) (newPacketlength >> 8), (byte) (newPacketlength & 0xff)};
        byte[] dataBlock = new byte[6 + newPacketlength];
        System.arraycopy(dataOpcode, 0, dataBlock, 0, 2);
        System.arraycopy(packetSize, 0, dataBlock, 2, 2);
        System.arraycopy(blockNum, 0, dataBlock, 4, 2);
        System.arraycopy(fileNames, currentPositionDIRQ, dataBlock, 6, newPacketlength);
        return dataBlock;
    }

    // Utility method to create and send ACK packets
    private byte[] createAckPacket(short blockNumber) {
        byte[] ackPacket = new byte[4];
        ackPacket[0] = (byte) ((short)(4) >> 8);
        ackPacket[1] = (byte) ((short)(4) & 0xff);
        ackPacket[2] = (byte) (blockNumber >> 8);
        ackPacket[3] = (byte) (blockNumber & 0xff);
        return ackPacket;
    }

    // Method to send the next data packet for RRQ/WRQ
    private byte[] createBcastPacket(byte bCastSign,byte[] message) {
        byte[] bcastPacket = new byte[message.length + 4]; // Opcode (2 bytes), operation (1 byte), fileNameBytes, and a terminating zero byte
        bcastPacket[0] = (byte) ((short) 9 >> 8);
        bcastPacket[1] = (byte) ((short) 9 & 0xff);
        bcastPacket[2] = bCastSign; // 0 for deletion, 1 for addition (assuming)
        System.arraycopy(message, 0, bcastPacket, 3, message.length);
        bcastPacket[bcastPacket.length - 1] = 0; // Terminating zero for the string
        return encoderDecoder.encode(bcastPacket);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
    private void initializeFileRepository() {
        File[] filesArray = new File(filesDir).listFiles();
        if (filesArray == null) {
            System.err.println("No files found in the directory or an I/O error occurred.");
            return;
        }
    
        for (File file : filesArray) {
            if (!file.isDirectory()) {
                try {
                    byte[] data = Files.readAllBytes(file.toPath());
                    int nullByteIndex = data.length; // Default to the full length of data
                    // Find the index of the first byte with a value of 0
                    for (int i = 0; i < data.length; i++) {
                        if (data[i] == 0) { // 0 represents the null byte in a byte array
                            nullByteIndex = i;
                            break;
                        }
                    }
                    // Trim the data up to the first null byte, if any was found
                    byte[] trimmedData = (nullByteIndex < data.length) ? Arrays.copyOf(data, nullByteIndex) : data;
                    FilesRepository.files.put(file.getName(), trimmedData);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    
    private void resetState() {
        currFile = "";
        currentBlock = 0;
        currentPositionRRQ = 0;
        currentPositionDIRQ = 0;
        currentOpcode = -1;
        isReadRequestComplete = true;
        isDirectoryRequestComplete = true;
        directoryRequestBlockNumber = 0;
        receivedBlockNumber = 0;
    }
}