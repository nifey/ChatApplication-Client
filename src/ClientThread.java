package sample;

import javafx.collections.ObservableList;
import javafx.scene.control.ListView;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;

class ClientThread implements Runnable{
    SocketChannel socketChannel;
    private Selector selector;
    private ByteBuffer readBuffer = ByteBuffer.allocate(4092);
    private HashMap pendingData = new HashMap();
    private List channelsToWrite = new ArrayList<SocketChannel>();

    private HashMap<String, List> UserChatMap = new HashMap<String, List>();
    private HashMap<String, List> GroupChatMap = new HashMap<String, List>();
    ObservableList<String> msgListView;
    ObservableList<String> userListView;
    ObservableList<String> groupListView;
    String currentChat;
    Boolean currentChatIsGroup = false;

    public ClientThread(String hostname, int port, ObservableList<String> msgList, ObservableList<String> userList, ObservableList<String> groupList) {
        System.out.println("DEBUG: ClientThread: Created");
        InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
        msgListView = msgList;
        userListView = userList;
        groupListView = groupList;
        try {
            this.socketChannel = SocketChannel.open(new InetSocketAddress("localhost",12121));
            this.socketChannel.configureBlocking(false);
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_READ);
            System.out.println("DEBUG: ClientThread: Connected to server");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run(){
        System.out.println("DEBUG: Client: Started");
        while(true){
            try {
                synchronized (channelsToWrite){
                    Iterator channels = channelsToWrite.iterator();
                    while(channels.hasNext()){
                        SocketChannel socketChannel = (SocketChannel) channels.next();
                        SelectionKey key = socketChannel.keyFor(selector);
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                    channelsToWrite.clear();
                }

                System.out.println("DEBUG: Client: Selector waiting for event");
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
                        System.out.println("DEBUG: Client: Accepted connection");
                    } else if (currentKey.isReadable()) {
                        this.read(currentKey);
                        System.out.println("DEBUG: Client: Finished reading data");
                    } else if (currentKey.isWritable()){
                        this.write(currentKey);
                        System.out.println("DEBUG: Client: Finished writing data");
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void write(SelectionKey key){
        SocketChannel socketChannel = (SocketChannel) key.channel();
        synchronized (pendingData) {
            List pendingWriteData = (List) this.pendingData.get(socketChannel);
            while (!pendingWriteData.isEmpty()) {
                System.out.println("DEBUG: Client: Found something to write");
                ByteBuffer buffer = (ByteBuffer) pendingWriteData.get(0);
                try {
                    socketChannel.write(buffer);
                    System.out.println("DEBUG: Client: Written Data");
                    if(buffer.remaining()>0){
                        break;
                    }
                    pendingWriteData.remove(0);
                } catch (IOException e){
                    e.printStackTrace();
                }
            }

            if(pendingWriteData.isEmpty()){
                key.interestOps(SelectionKey.OP_READ);
                System.out.println("DEBUG: Client: Nothing more to write in that channel");
            }
        }
    }

    private void read (SelectionKey key){
        SocketChannel socketChannel = (SocketChannel) key.channel();
        readBuffer.clear();
        int numRead;
        try {
            numRead = socketChannel.read(readBuffer);
        } catch (IOException e){
            key.cancel();
            try {
                socketChannel.close();
            } catch (IOException e1){
                e1.printStackTrace();
            }
            return;
        }
        if(numRead == -1){
            try {
                key.channel().close();
            } catch (IOException e){
                e.printStackTrace();
            }
            key.cancel();
            return;
        }

        readBuffer.flip();
        byte[] bytes = new byte[readBuffer.remaining()];
        System.out.println("DEBUG: Client: Read "+readBuffer.remaining()+" Characters");
        readBuffer.get(bytes);
        String read = new String(bytes);
        System.out.println("DEBUG: Client: Read :"+read+" of length "+read.length());
        int in = read.lastIndexOf("##");
        if (in != -1) {
            read = read.substring(0, in);
            for (String taskStr : read.split("##")) {
                this.handleMessage(taskStr);
                System.out.println("DEBUG: Client: Msg to be handled: " + taskStr);
            }
        }
    }

    private void handleMessage(String msgText){
        String[] msgParts = msgText.split("\\$");
        switch (msgParts[0]){
            case "MSG":
                if(msgParts.length>2) {
                    displayMessage(msgParts[1], msgParts[2]);
                }
                break;
            case "INFO":
                if(msgParts.length>1) {
                    displayInfo(msgParts[1]);
                }
                break;
            case "GMSG":
                if(msgParts.length>3){
                    displayGroupMessage(msgParts[1],msgParts[2],msgParts[3]);
                }
                break;
            case "USERS":
                updateUsers(msgParts[1]);
                break;
            case "GROUPS":
                updateGroups(msgParts[1]);
                break;
        }
    }

    void updateUsers(String users){
        synchronized (userListView) {
            userListView.clear();
            for (String user : users.split(",")) {
                userListView.add(user);
            }
        }
    }

    void updateGroups(String groups){
        synchronized (groupListView) {
            groupListView.clear();
            for (String group : groups.split(",")) {
                groupListView.add(group);
            }
        }
    }

    void displayMessage(String sender, String msg){
        synchronized (msgListView) {
            msgListView.add(sender + " : " + msg);
        }
    }

    void displayGroupMessage(String grpName, String sender, String msg){
        msgListView.add(grpName + " => " + sender + " : " + msg);
    }

    void displayInfo(String info){
        msgListView.add(info);
    }

    void send(SocketChannel socketChannel, String msg){
        synchronized (channelsToWrite) {
            channelsToWrite.add(socketChannel);
            synchronized (pendingData) {
                List dataList = (List) pendingData.get(socketChannel);
                if(dataList == null){
                    dataList = new ArrayList();
                    pendingData.put(socketChannel, dataList);
                }
                dataList.add(ByteBuffer.wrap(msg.getBytes()));
                System.out.println("DEBUG: Client: Data added to pending data");
            }
        }
        selector.wakeup();
    }

}