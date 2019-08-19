package sample;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class FileSenderThread implements Runnable {
        private Logger logger = Logger.getLogger(FileSenderThread.class.getName());
        private SocketChannel socketChannel;
        private Selector selector;
        private ByteBuffer readBuffer = ByteBuffer.allocate(4092);
        private HashMap pendingMessages = new HashMap();
        private List channelsToWrite = new ArrayList<SocketChannel>();
        private List fileList = new ArrayList<String>();
        private String filename;
        private Main.ClientThread ct;
        private String currentChat;
        private Boolean currentChatIsGroup;
        private Boolean running = true;

        private void log(String msg){
            logger.info(FileSenderThread.class.getName()+" : "+msg);
        }

        public FileSenderThread(String hostname, int port, Main.ClientThread ct, String filename, String currentChat, Boolean currentChatIsGroup){
            InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
            try {
                this.ct = ct;
                this.filename = filename;
                this.currentChat = currentChat;
                this.currentChatIsGroup = currentChatIsGroup;
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
                            log("Accepted connection");
                        } else if (currentKey.isReadable()) {
                            this.read(currentKey);
                        } else if (currentKey.isWritable()){
                            this.write(currentKey);
                        }
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }

        private void write(SelectionKey key){
            SocketChannel socketChannel = (SocketChannel) key.channel();
                Path path = Paths.get( this.filename);
                if (Files.exists(path, new LinkOption[]{LinkOption.NOFOLLOW_LINKS})) {
                    try {
                        File file = new File(this.filename);
                        if(file.isDirectory()){
                            this.ct.displayInfo(this.filename + " is a directory");
                            try {
                                socketChannel.close();
                                running = false;
                            } catch (IOException e){
                                e.printStackTrace();
                            }
                        } else if(file.length()!=0 && file.isFile()) {
                            String msg = "RECEIVE$" + file.length() + "##";
                            ByteBuffer receiveMsg = ByteBuffer.wrap(msg.getBytes());
                            while (receiveMsg.remaining() > 0) {
                                socketChannel.write(receiveMsg);
                            }

                            FileChannel fc = new FileInputStream(file).getChannel();
                            ByteBuffer buf = ByteBuffer.allocate(4096);
                            int numRead = fc.read(buf);
                            while (numRead != -1) {
                                int bytesRem;
                                buf.flip();
                                do {
                                    socketChannel.write(buf);
                                    bytesRem = buf.remaining();
                                } while (bytesRem > 0);
                                buf.clear();
                                numRead = fc.read(buf);
                            }
                            log("Finished writing file " + filename + " to "+ socketChannel.getRemoteAddress());
                            fc.close();
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    this.ct.displayInfo("File "+filename+" not found");
                    try {
                        socketChannel.close();
                        running = false;
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
        }

        private void read (SelectionKey key) {
            SocketChannel socketChannel = (SocketChannel) key.channel();
            readBuffer.clear();
            int numRead;
            try {
                numRead = socketChannel.read(readBuffer);
            } catch (IOException e) {
                key.cancel();
                try {
                    socketChannel.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                return;
            }
            if (numRead == -1) {
                try {
                    key.channel().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                key.cancel();
                return;
            }

            readBuffer.flip();
            byte[] bytes = new byte[readBuffer.remaining()];
            readBuffer.get(bytes);
            String read = new String(bytes);
            log("Read :" + read + " of length " + read.length());
            int in = read.lastIndexOf("##");
            if (in != -1) {
                read = read.substring(0, in);
                String[] msgParts = read.split("\\$");
                if (msgParts[0].equals("RECEIVED")) {
                    if (msgParts.length > 2) {
                        String keyString = msgParts[1];
                        int numberOfBytes = -1;
                        try {
                            numberOfBytes = Integer.parseInt(msgParts[2]);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                        if (numberOfBytes > 0) {
                            String newName = filename.substring(filename.lastIndexOf("/") + 1);
                            if (currentChatIsGroup) {
                                this.ct.send(this.ct.socketChannel, "GFILE$" + currentChat + "$" + newName + "$" + msgParts[2] + "$" + msgParts[1] + "##");
                                this.ct.displayInfo("File " + filename + " has been sent to the group " + currentChat);
                            } else {
                                this.ct.send(this.ct.socketChannel, "FILE$" + currentChat + "$" + newName + "$" + msgParts[2] + "$" + msgParts[1] + "##");
                                this.ct.displayInfo("File " + filename + " has been sent to " + currentChat);
                            }
                            try {
                                this.socketChannel.close();
                                this.running = false;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
}
