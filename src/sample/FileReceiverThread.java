package sample;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Logger;

public class FileReceiverThread implements Runnable {
    private Logger logger = Logger.getLogger(FileReceiverThread.class.getName());
    private SocketChannel socketChannel;
    private Selector selector;
    private ByteBuffer readBuffer = ByteBuffer.allocate(4092);
    private String fileDirPath = "/home/nihaal/filedir/";
    private String filename;
    private Main.ClientThread ct;
    private Boolean running = true;
    private int numberOfBytes;
    private String keyString;
    private String sender;
    private String grpName;
    private Boolean isGroup;

    private void log(String msg){
        logger.info(FileReceiverThread.class.getName()+" : "+msg);
    }

    public FileReceiverThread(String hostname, int port, Main.ClientThread ct, String filename, int numberOfBytes, String keyString, String sender, Boolean isGroup, String grpName){
        InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
        try {
            this.ct = ct;
            this.filename = filename;
            this.numberOfBytes = numberOfBytes;
            this.keyString = keyString;
            this.sender = sender;
            this.isGroup = isGroup;
            this.grpName = grpName;
            this.socketChannel = SocketChannel.open(serverAddress);
            this.socketChannel.configureBlocking(false);
            this.selector = Selector.open();
            this.socketChannel.register(selector, SelectionKey.OP_WRITE);
        } catch (IOException e) {
            log("Could not connect to file server");
            e.printStackTrace();
        }
    }

    public void run(){
        log("Started");
        while(running){
            try {
                log("Selector waiting for event");
                if(this.selector == null){
                    this.running = false;
                    continue;
                }
                this.selector.select();
                Iterator<SelectionKey> iter = this.selector.selectedKeys().iterator();

                while (iter.hasNext()) {
                    SelectionKey currentKey = iter.next();
                    iter.remove();
                    if(!currentKey.isValid()){
                        continue;
                    }
                    if (currentKey.isAcceptable()) {
                        socketChannel.finishConnect();
                        log("Accepted connection from "+ socketChannel.getRemoteAddress());
                    } else if (currentKey.isReadable()) {
                        this.receiveFile(currentKey);
                    } else if (currentKey.isWritable()){
                        this.write(currentKey);
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void write(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        String msg = "SEND$" + this.keyString + "##";
        ByteBuffer receiveMsg = ByteBuffer.wrap(msg.getBytes());
        try {
            while (receiveMsg.remaining() > 0) {
                socketChannel.write(receiveMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    private void receiveFile(SelectionKey key){
        SocketChannel socketChannel = (SocketChannel) key.channel();
        try {
            FileChannel fc = new FileOutputStream(new File(fileDirPath+"/"+this.filename)).getChannel();
            ByteBuffer buf = ByteBuffer.allocate(512);
            long numRead = 0;
            while(numRead < numberOfBytes){
                long bytes = socketChannel.read(buf);
                if(bytes == -1){
                    break;
                }
                buf.flip();
                while(buf.remaining()>0) {
                    fc.write(buf);
                }
                buf.clear();
                numRead = numRead + bytes;
            }
            fc.close();
            log("Received file with keyString "+keyString);
            if(this.isGroup) {
                this.ct.displayInfo("Recieved file " + filename + " from " + sender + " in the group " + grpName);
            } else {
                this.ct.displayInfo("Recieved file " + filename + " from " + sender );
            }
            running = false;
        }catch (Exception e ){
            e.printStackTrace();
        }
    }
}
