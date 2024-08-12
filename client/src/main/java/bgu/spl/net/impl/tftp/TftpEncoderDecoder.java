package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private byte[] bytes = new byte[1 << 10]; // Start with 1k buffer
    private int currentIndex = 0; // Length of the current message
    private short currentOpcode = -1; // To store the decoded opcode
    private int blockNum = 0;
    private byte[] result;
    
    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if(currentIndex == 0){
            pushByte(nextByte);
            return null;
        }
        else if(currentIndex == 1){
            pushByte(nextByte);
            currentOpcode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]));
            if(currentOpcode==6 | currentOpcode==10){
                result = Arrays.copyOf(bytes,currentIndex);
                resetState();
                return result;
            }
            return null;
        }
        else if (currentOpcode!=-1){
            switch (currentOpcode) {
                case 1: // RRQ
                    return decodeNextByteRRQ(nextByte);
                case 2: // WRQ
                    return decodeNextByteWRQ(nextByte);
                case 3: //DATA
                    return decodeNextByteData(nextByte);
                case 4: // ACK
                    return decodeNextByteACK(nextByte);
                case 5: // ERROR
                    return decodeNextByteError(nextByte);
                case 6: // DIRQ
                    result = Arrays.copyOf(bytes,currentIndex);
                    resetState();
                    return result;
                case 7: // LOGRQ
                    return decodeNextByteLOGRQ(nextByte);
                case 8: // DELRQ
                    return decodeNextByteDELRQ(nextByte);                        
                case 9: // BCAST
                    return decodeNextByteBcast(nextByte);
                case 10: //DISC
                    result = Arrays.copyOf(bytes,currentIndex);
                    resetState();
                    return result;    
            }
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private byte[] decodeNextByteRRQ(byte nextByte) {
        if(nextByte == 0) {
            pushByte(nextByte);
            byte[] result = Arrays.copyOf(bytes,currentIndex);
            resetState();
            return result;
        } else {
            pushByte(nextByte);
            return null;
        }
    }

    // Placeholder for decodeNextByteFilenameOrUsername method
    private byte[] decodeNextByteWRQ(byte nextByte) {
        if(nextByte == 0) {
            pushByte(nextByte);
            byte[] result = Arrays.copyOf(bytes,currentIndex);
            resetState();
            return result;
        } else {
            pushByte(nextByte);
            return null;
        }
    }
    

    // Placeholder methods for other specific operation decoding
    private byte[] decodeNextByteData(byte nextByte) {
        if(currentIndex==4){
            blockNum = (short) (((short) bytes[2]) << 8 | (short) (bytes[3]) & 0x00ff);
            pushByte(nextByte);
            return null;
        }
        if(currentIndex == blockNum+5){
            pushByte(nextByte);
            byte[] result = Arrays.copyOf(bytes,currentIndex);
            resetState();
            return result;
        }
        else if(currentIndex < blockNum+5){
            pushByte(nextByte);
            return null;
        }
        return null;
    }


    private byte[] decodeNextByteACK(byte nextByte) {
        if(currentIndex==2){
            pushByte(nextByte);
            return null;
        }
        else if(currentIndex==3){
            pushByte(nextByte);
            byte[] result = Arrays.copyOf(bytes,currentIndex);
            resetState();
            return result;
        }
        return null;
    }


    private byte[] decodeNextByteError(byte nextByte) {
        // Logic for decoding ERROR packets
        if(nextByte == 0 && currentIndex>3) {
            pushByte(nextByte);
            byte[] result = Arrays.copyOf(bytes,currentIndex);
            resetState();
            return result;
        } else {
            pushByte(nextByte);
            return null;
        }
    }

    private byte[] decodeNextByteBcast(byte nextByte) {
        if(nextByte == 0 && currentIndex>2) {
            pushByte(nextByte);
            byte[] result = Arrays.copyOf(bytes,currentIndex);
            resetState();
            return result;
        } else {
            pushByte(nextByte);
            return null;
        }
    }
  
    private byte[] decodeNextByteLOGRQ(byte nextByte) {
        if(nextByte == 0) {
            pushByte(nextByte);
            byte[] result = Arrays.copyOf(bytes,currentIndex);
            resetState();
            return result;
        } else {
            pushByte(nextByte);
            return null;
        }
    }
    private byte[] decodeNextByteDELRQ(byte nextByte) {
        if(nextByte == 0) {
            pushByte(nextByte);
            byte[] result = Arrays.copyOf(bytes,currentIndex);
            resetState();
            return result;
        } else {
            pushByte(nextByte);
            return null;
        }
    }
    private void pushByte(byte nextByte) {
        if (currentIndex>= bytes.length) {
            bytes = Arrays.copyOf(bytes, currentIndex * 2);
        }
        bytes[currentIndex] = nextByte;
        currentIndex++;
    }
    private void resetState() {
        currentOpcode = -1;
        currentIndex = 0;
        bytes = new byte[1 << 10];
        blockNum = 0;
    }
}