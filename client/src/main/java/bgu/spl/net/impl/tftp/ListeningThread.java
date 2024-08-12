package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import bgu.spl.net.api.MessagingProtocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;


public class ListeningThread implements Runnable, MessagingProtocol<byte[]>{
 
    private Thread listeningThread;
    private BufferedInputStream in ;
    private BufferedOutputStream out ;
    private boolean logged;
    private int state;
    private TftpEncoderDecoder encdec;
    private boolean shouldTerminate;
    private File path;
    File fileInProcess;
    private Queue<byte[]> dirqToPrint;
    FileInputStream fis;
    private Socket sock; 
    FileOutputStream fos;
     
    
     public ListeningThread(BufferedInputStream in,BufferedOutputStream out, Socket sock){
        this.in=in;
        this.out=out;
        logged=false;
        this.state=-1;
        encdec=new TftpEncoderDecoder();
        this.shouldTerminate=false;
        this.path = new File(System.getProperty("user.dir"));
        this.dirqToPrint=new LinkedList<>();
        this.fileInProcess=null;
        fis=null;
        this.sock=sock;
        fos=null;
        
    
     }
     
        
     
     public void run(){
        this.listeningThread=Thread.currentThread();
        while (!shouldTerminate) {
            
             process(getResult());
             

        
            
          
        }
    
     }
     public String removeCommandWord(String action){
        int spaceIndex = action.indexOf(' ');
        // If spaceIndex is -1, it means there's no space in the string
        String resultString="";
        if (spaceIndex != -1) {
            // Extract the substring starting from the character immediately after the space
            resultString = action.substring(spaceIndex + 1);
        }
            return resultString;

           

     }
     private byte[] getResult(){
        byte[] result=null;
        int b=-1;
        try {
            while((result==null)&&((b=in.read())>-1)){
               result=encdec.decodeNextByte((byte)b);
            }
            
            if(b==-1){// the socket is closed
                System.out.println( "socket is closed");
                this.shouldTerminate=true;
                this.terminate();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            
        }

        return result;
     }
    public void setFileInProcess(File file){
        this.fileInProcess=file;
       
        
    } 
     private byte[] buildData(byte[] data,int dataAmount, short blocknum){
        
        byte[] pack=new byte[dataAmount+6];
        pack[0]=(byte)0;
        pack[1]=(byte)3;
        pack[2]=( byte ) ( dataAmount >> 8);
        pack[3]= ( byte ) ( dataAmount & 0xff );
        pack[4] = ( byte ) ( blocknum >> 8);
        pack[5]= ( byte ) ( blocknum & 0xff );
        
        for(int i=6;i<pack.length;i++){
            pack[i]=data[i-6];
        }
        return pack;


     }
     public short opCode(String action){
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
    }
    System.out.println("the command you entered is not valid, please try again");
    return -1;
    }
    
    public byte[] getBytes(String s){
        short opcode=this.opCode(s);
        if((opcode==6)||(opcode==10)){
            byte[] packet=new byte[2];
            packet[0]= (byte)(0);
            packet[1]= (byte)(opcode);
            return packet;
        }
        if((opcode==7)||(opcode==8)||(opcode==1)||(opcode==2)){
            String[] words = s.split(" ");
        byte[] filename=words[1].getBytes(StandardCharsets.UTF_8);
        byte[] packet =createBytesArrayWithFileName(opcode,filename); 
        return packet;
        }
        return null;
    }
    
    public static byte[] createBytesArrayWithFileName(int opcode,byte[] filename){
        byte[] packet=new byte[2+filename.length+1];
        packet[0]= (byte)(0);
        packet[1]= (byte)(opcode);
        for(int i=0;i<filename.length;i++){
            packet[i+2]=filename[i];
        }
        packet[packet.length-1]= (byte)(0);
        return packet;
    }
    @Override
    public byte[] process (byte[] msg) {
        short opcode = ( short ) ((( short ) msg[0]) << 8 | ( short ) ( msg[1]) );
        switch (opcode) {
            case 3://data
                if(this.state==1){
                    sendACK(msg);
                    try {
                        if(fos==null){
                            this.fos= new FileOutputStream(fileInProcess,true);
                        }
                        fos.write(msg, 6, msg.length-6);
                        if(lastPacket(msg)){
                            System.out.println("RRQ "+fileInProcess.getName()+" complete");
                            this.state=0;
                            fos=null;
                            synchronized(this){
                                this.notifyAll();}
                        }
        }           catch (IOException e) {
                         System.out.println("An error occurred while writing to the file: " + e.getMessage());
        }
                    
                    

    }
                if(this.state==6){
                    this.dirqToPrint.add(msg);
                    sendACK(msg);
                    if(lastPacket(msg)){
                        this.printDIRQ();
                        this.state=0;
                        synchronized(this){
                            this.notifyAll();}
                    }
                    
                    

                }
                break;
            case 4://ACK
                if(this.state==2){
                    short blocknum = ( short ) ((( short ) msg [2]) << 8 | ( short ) ( msg [3]) & 0xff );
                    System.out.println("ACK "+blocknum);
                    try {
                        if(fis==null){
                        fis=new FileInputStream(fileInProcess);
                    }
                        byte[] buffer= new byte[512];
                        int readamount=fis.read(buffer);
                        if(readamount!=-1){
                            this.out.write(buildData(buffer,readamount, (short)(blocknum+1)));
                            this.out.flush();
                        }
                        else{
                            System.out.println( "WRQ "+fileInProcess.getName()+" complete");
                            state=0;
                            this.fileInProcess=null;
                            fis.close();
                            this.fis=null;
                            synchronized(this){
                                this.notifyAll();}
                        }
                    } catch (FileNotFoundException e) {
                        System.out.println("couldnt initiate fis");
                    } catch (IOException e) {
                        System.out.println("couldnt read from file");
                    }
                    

                }
                if(this.state==7){
                    this.logged=true;
                    System.out.println("ACK "+( short ) ((( short ) msg [2]) << 8 | ( short ) ( msg [3]) & 0xff ));
                    System.out.println("you logged in succesfully");
                    state=0;
                    synchronized(this){
                    this.notifyAll();}

                }
                if(this.state==8){
                    System.out.println("ACK "+( short ) ((( short ) msg [2]) << 8 | ( short ) ( msg [3]) & 0xff ));
                    state=0;
                    synchronized(this){
                        this.notifyAll();}
                }
                if(this.state==10){
                    System.out.println("ACK "+( short ) ((( short ) msg [2]) << 8 | ( short ) ( msg [3]) & 0xff ));
                    state=0;
                    this.terminate();
                }
                
                break;
            case 5://error
                if(this.state==1){
                    this.fileInProcess.delete();
                    this.fileInProcess=null;
                    priNTERROR(msg);
                    state=0;
                    synchronized(this){
                        this.notifyAll();}


                }
                if(this.state==2){
                    priNTERROR(msg);
                    if(fis!=null){
                        try {
                            fis.close();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        fis=null;
                    }
                    this.fileInProcess=null;
                    state=0;
                    synchronized(this){
                        this.notifyAll();}
                }
                if(this.state==6){
                    priNTERROR(msg);
                    state=0;
                    this.dirqToPrint=new LinkedList<>();
                    synchronized(this){
                        this.notifyAll();}
                }
                if(this.state==7){      
                    priNTERROR(msg);
                    state=0;
                    synchronized(this){
                        this.notifyAll();}
                }
                if(this.state==8){
                    priNTERROR(msg);
                    state=0;
                    synchronized(this){
                this.notifyAll();}}
                if(this.state==10){
                    priNTERROR(msg);
                    state=0;
                    synchronized(this){
                        this.notifyAll();}
                }
                break;
            case 9://bcast
                String filename=new String(msg,3,msg.length-4,StandardCharsets.UTF_8);
                if(msg[2]==0){
                    System.out.println(  "BCAST del "+filename);
                }
                else{System.out.println(  "BCAST add "+filename);}
                break;

                
        
            
        }
        return null;

    }
    public void printDIRQ(){
        int startNameIndex=6;
        int endNameIndex=6;
        byte[] msg;
        while(!this.dirqToPrint.isEmpty()){
            msg=this.dirqToPrint.poll();
            while(endNameIndex<msg.length){
                if((msg[endNameIndex]==(byte)0)||(endNameIndex==msg.length-1)){
                    if(endNameIndex==msg.length-1){
                        endNameIndex++;
                    }
                    System.out.println(new String(msg, startNameIndex, endNameIndex-startNameIndex,StandardCharsets.UTF_8));
                    endNameIndex=endNameIndex+1;
                    startNameIndex=endNameIndex;
                
                }
                else{endNameIndex++;}
            }
        }

    }
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
    public static void priNTERROR(byte[] error){
        String erroString=new String(error,4,error.length-4,StandardCharsets.UTF_8);
        short errortype = ( short ) ((( short ) error[2]) << 8 | ( short ) ( error[3]) );
        System.out.println("ERROR "+errortype+" "+erroString);
    }
    public void terminate(){
        this.shouldTerminate=true;
        synchronized(this){
            this.notifyAll();}
       
    }
    public void setState(int opcode){
        this.state=opcode;
    }
    public int getState(){
        return this.state;
    }
    public void sendACK(byte[] data){
        byte[] ACK=new byte[4];
        ACK[0]=(byte)0;
        ACK[1]=(byte)4;
        ACK[2]=data[4];
        ACK[3]=data[5];
        try {
            this.out.write(ACK);
            this.out.flush();
        } catch (IOException e) {
            
        }

    }
    public boolean lastPacket(byte[] bytes){
        short psize = ( short ) ((( short ) bytes[2]) << 8 | ( short ) ( bytes[3]) );
        return(psize<512);
    }
    public boolean isLogged(){
        return this.logged;
    }
    }
    
