package bgu.spl.net.impl.tftp;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.io.*;


public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            args = new String[]{"127.0.0.1", "hello"};
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, message");
            System.exit(1);
        }

        //BufferedReader and BufferedWriter automatically using UTF-8 encoding
        try  
           
            {
            Socket sock = new Socket(args[0],7777);
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());
            

        
        Scanner scanner = new Scanner(System.in);
        ListeningThread listeningThread = new ListeningThread(in,out,sock);
        Thread lt=new Thread(listeningThread);
        lt.start();
        Thread keyboThread = new Thread(() -> {
            File path = new File(System.getProperty("user.dir"));
            while (!listeningThread.shouldTerminate()) {
                String nextAction=scanner.nextLine();
                while(!isValidInput(nextAction)){
                    System.out.println("invalid command");
                    nextAction=scanner.nextLine();
                }
                listeningThread.setState(opCode(nextAction));
                byte[] bytes=getBytes(nextAction, listeningThread);
                /*try {
                    out.write(bytes);
                } catch (IOException e) {
                   System.out.println("cant write to server");
                }*/
                int op=opCode(nextAction);
                switch(op){
                    case 1: // RRQ
                        handleRRQ(listeningThread.removeCommandWord(nextAction), listeningThread, path, bytes, out);
                        break;
                    case 2: // WRQ
                       handleWRQ(listeningThread.removeCommandWord(nextAction), listeningThread, path, bytes, out);
                       break;
                    case 6: // DIRQ
                        writeToServer(bytes,out);
                        
                        break;
                    case 7: // LOGRQ
                        writeToServer(bytes,out);
                        
                        break;
                    case 8: // DELRQ
                        writeToServer(bytes,out); 
                          
                        break;                     
                    case 10: //DISC
                       
                        if(listeningThread.isLogged()){writeToServer(bytes,out);}
                        else{listeningThread.setState(0);
                        listeningThread.terminate();};
                        break;
                }
               

                if(listeningThread.getState()!=0){
                 try {
                    synchronized(listeningThread){
                    listeningThread.wait();}
                } catch (InterruptedException ignored) {
                   
                }
            }
                }
                try {
                    sock.close();
                    in.close();
                    out.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                
            }
           
    );
        
        
        
        
        keyboThread.start();
        keyboThread.join();
        
        } catch (Exception ignored) {
            
        } 

       
    }
    public static void handleWRQ(String filename,ListeningThread listeningThread, File path, byte[] bytes,BufferedOutputStream out){
        if(!listeningThread.isLogged()){
            System.out.println("you are not logged in, please log in first");
            listeningThread.setState(0);
            return;
        }
        
        if(fileExists(filename,path)){

            listeningThread.setFileInProcess(new File(path,filename));
            writeToServer(bytes, out);}
        else{
            System.out.println( "file does not exists");
            listeningThread.setState(0);
        }

    }
    public static void handleRRQ(String filename,ListeningThread listeningThread, File path, byte[] bytes,BufferedOutputStream out){
        if(fileExists(filename,path)){
            System.out.println( "file already exists");
            listeningThread.setState(0);
        }
        else{
            File downloadingfile = new File(path, filename);
        
        try {
            // Create the new file
            downloadingfile.createNewFile();
            
        } catch (IOException e) {
            System.out.println("An error occurred while creating the file: " + e.getMessage());
            listeningThread.setState(0);
            return;
        }
        listeningThread.setFileInProcess(downloadingfile);
            writeToServer(bytes, out);
        }

    }
    public static void writeToServer(byte[] bytes,BufferedOutputStream out){
        try {
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
           System.out.println("cant write to server");
        }
        
    }
    private static boolean fileExists(String filename,File path){

        File file = new File(path, filename);
        return file.exists();
    } 
    public static boolean isValidInput(String input){
        int op=opCode(input);
        if(op==-1){
            return false;
        }
        //if((!listeningThread.isLogged())&&(op!=7)){
        //    return false;

        //}
        return true;
    }
    public static byte[] getBytes(String s,ListeningThread listeningThread){
        short opcode=opCode(s);
        if((opcode==6)||(opcode==10)){
            byte[] packet=new byte[2];
            packet[0]= (byte)(0);
            packet[1]= (byte)(opcode);
            return packet;
        }
        if((opcode==7)||(opcode==8)||(opcode==1)||(opcode==2)){
        byte[] filename=listeningThread.removeCommandWord(s).getBytes(StandardCharsets.UTF_8);
        byte[] packet =listeningThread.createBytesArrayWithFileName(opcode,filename); 
        return packet;
        }
        return null;
    }
    public static short opCode(String action){
        if(action!=null){
            if(action.startsWith("RRQ")){
                 return 1;
           }
           if(action.startsWith("WRQ")){
                 return 2;
          }
          if(action.startsWith("DIRQ")){
                 return 6;
         }
         if(action.startsWith("LOGRQ")){
                 return 7;
        }
        if(action.startsWith("DELRQ")){
            return 8;
        }
        if(action.startsWith("DISC")){
            return 10;
        }
        return -1;
    }
    return -1;

      

    
    
    
    

}}
