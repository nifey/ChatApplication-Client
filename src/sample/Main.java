package sample;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;


public class Main extends Application {
    ListView<String> userList = new ListView<String>();
    ListView<String> msgList = new ListView<String>();
    ListView<String> grpList = new ListView<String>();
    String currentChat = new String();
    Boolean currentChatIsGroup = false;
    ClientThread ct = new ClientThread("localhost", 12121);

    @Override
    public void start(Stage primaryStage) throws Exception{
        Thread t = new Thread(ct);
        t.setDaemon(true);
        t.start();

        Group root = new Group();
        VBox vPane = new VBox();
        VBox smallVPane = new VBox();
        HBox hPane1 = new HBox();
        hPane1.setPadding(new Insets(10));
        hPane1.setSpacing(10);
        HBox hPane2 = new HBox();
        hPane2.setPadding(new Insets(10));
        hPane2.setSpacing(10);

        this.userList.setPrefSize(150,210);
        this.grpList.setPrefSize(150,210);
        smallVPane.getChildren().addAll(this.userList, this.grpList);
        smallVPane.setSpacing(10);
        this.msgList.setPrefSize(450, 430);

        hPane1.getChildren().addAll(smallVPane,this.msgList);
        TextField textField = new TextField();
        textField.setPrefWidth(500);
        textField.setPromptText("enter your message");
        Button sendButton = new Button();
        sendButton.setPrefSize(100,10);
        sendButton.setText("Send");
        sendButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                sendMessage(textField.getText());
                textField.clear();
            }
        });
        hPane2.getChildren().addAll(textField, sendButton);
        vPane.getChildren().addAll(hPane1,hPane2);

        root.getChildren().add(vPane);
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 630, 500));
        primaryStage.show();

        //TODO handle onapplication exit to logout
    }

    private void updateMsgList(List list){
        msgList.getItems().clear();
        msgList.getItems().addAll(list);
    }

    private void updateGrpList(List list){
        grpList.getItems().clear();
        grpList.getItems().addAll(list);
    }

    private void updateUserList(List list){
        userList.getItems().clear();
        userList.getItems().addAll(list);
    }

    private void sendMessage(String msg) {
        if(msg.startsWith("\\")){
            this.ct.send(ct.socketChannel, msg.substring(1));
            return;
        } else if (this.currentChatIsGroup) {
            this.ct.send(ct.socketChannel, "MSG$" + this.currentChat + "$" + msg +"##");
        } else {
            this.ct.send(ct.socketChannel, "GMSG$" + this.currentChat + "$" + msg + "##");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }


    class ClientThread implements Runnable{
        SocketChannel socketChannel;
        private Selector selector;
        private ByteBuffer readBuffer = ByteBuffer.allocate(4092);
        private HashMap pendingData = new HashMap();
        private List channelsToWrite = new ArrayList<SocketChannel>();

        private HashMap<String, List> UserChatMap = new HashMap<String, List>();
        private HashMap<String, List> GroupChatMap = new HashMap<String, List>();
        String currentChat;
        Boolean currentChatIsGroup = false;

        public ClientThread(String hostname, int port) {
            System.out.println("DEBUG: ClientThread: Created");
            InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
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
                    updateUsersLater(msgParts[1]);
                    break;
                case "GROUPS":
                    updateGroups(msgParts[1]);
                    break;
            }
        }

        void updateUsersLater(String users){
            ArrayList<String> userList = new ArrayList<String>();
            for (String user : users.split(",")) {
                userList.add(user);
            }
            Platform.runLater(new Runnable (){
                @Override
                public void run(){
                    updateUserList(userList);
                }
            });
        }

        void updateGroups(String groups){
            ArrayList<String> groupList = new ArrayList<String>();
            for (String group : groups.split(",")) {
                groupList.add(group);
            }
            Platform.runLater(new Runnable (){
                @Override
                public void run(){
                    updateGrpList(groupList);
                }
            });
        }

        void displayMessage(String sender, String msg){
            ArrayList<String> msgList = new ArrayList<String>();
            msgList.add(sender + " : " + msg);
            Platform.runLater(new Runnable (){
                @Override
                public void run(){
                    updateMsgList(msgList);
                }
            });
        }

        void displayGroupMessage(String grpName, String sender, String msg){
            ArrayList<String> msgList = new ArrayList<String>();
            msgList.add(grpName + " => " + sender + " : " + msg);
            Platform.runLater(new Runnable (){
                @Override
                public void run(){
                    updateMsgList(msgList);
                }
            });
        }

        void displayInfo(String info){
            ArrayList<String> msgList = new ArrayList<String>();
            msgList.add(info);
            Platform.runLater(new Runnable (){
                @Override
                public void run(){
                    updateMsgList(msgList);
                }
            });
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
}
