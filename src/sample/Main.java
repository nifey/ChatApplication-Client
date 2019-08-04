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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
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
    ListView<String> infoList = new ListView<String>();
    ClientThread ct = new ClientThread("localhost", 12121);

    @Override
    public void start(Stage primaryStage) throws Exception{
        Thread t = new Thread(ct);
        t.setDaemon(true);
        t.start();

        Group root = new Group();
        VBox vPane = new VBox();

        this.userList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                ct.switchToUser(userList.getSelectionModel().getSelectedItem());
            }
        });

        this.grpList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                ct.switchToGroup(grpList.getSelectionModel().getSelectedItem());
            }
        });

        VBox smallVPane1 = new VBox();
        this.userList.setPrefSize(150,210);
        this.grpList.setPrefSize(150,210);
        smallVPane1.getChildren().addAll(this.userList, this.grpList);
        smallVPane1.setSpacing(10);

        VBox smallVPane2 = new VBox();
        this.infoList.setPrefSize(450, 50);
        this.msgList.setPrefSize(450, 370);
        smallVPane2.getChildren().addAll(this.infoList, this.msgList);

        smallVPane2.setSpacing(10);
        HBox hPane1 = new HBox();
        hPane1.setPadding(new Insets(10));
        hPane1.setSpacing(10);
        hPane1.getChildren().addAll(smallVPane1,smallVPane2);

        TextField textField = new TextField();
        textField.setPrefWidth(500);
        textField.setPromptText("enter your message");
        textField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent keyEvent) {
                if(keyEvent.getCode() == KeyCode.ENTER && !textField.getText().isEmpty()){
                    ct.sendMessage(textField.getText());
                    textField.clear();
                }
            }
        });

        Button sendButton = new Button();
        sendButton.setPrefSize(100,10);
        sendButton.setText("Send");
        sendButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                ct.sendMessage(textField.getText());
                textField.clear();
            }
        });

        HBox hPane2 = new HBox();
        hPane2.setPadding(new Insets(10));
        hPane2.setSpacing(10);
        hPane2.getChildren().addAll(textField, sendButton);

        vPane.getChildren().addAll(hPane1,hPane2);
        root.getChildren().add(vPane);

        primaryStage.setTitle("Chat Client");
        primaryStage.setScene(new Scene(root, 630, 500));
        primaryStage.show();

    }

    private void updateMsgList(List list){
        msgList.getItems().clear();
        msgList.getItems().addAll(list);
    }

    private void updateGrpList(List list){
        grpList.getItems().clear();
        grpList.getItems().addAll(list);
    }

    private void clearGrpList(){
        grpList.getItems().clear();
    }

    private void updateUserList(List list){
        userList.getItems().clear();
        userList.getItems().addAll(list);
    }

    private void updateInfoList(List list){
        infoList.getItems().clear();
        infoList.getItems().addAll(list);
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

        private HashMap<String, List> userChatMap = new HashMap<String, List>();
        private HashMap<String, List> groupChatMap = new HashMap<String, List>();
        private List<String> infoChatList = new ArrayList<String>();
        String currentChat = new String("");
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

        private void sendMessage(String msg) {
            if (msg.startsWith("\\")) {
                String[] msgParts = msg.split(" ");
                switch (msgParts[0]){
                    case "\\login":
                        if(msgParts.length>1) {
                            this.send(ct.socketChannel, "LOGIN$" + msgParts[1] + "##");
                        }
                        break;
                    case "\\gcreate":
                        if(msgParts.length>1) {
                            this.send(ct.socketChannel, "GCREATE$" + msgParts[1] + "##");
                        }
                        break;
                    case "\\gdelete":
                        if(msgParts.length>1) {
                            this.send(ct.socketChannel, "GDELETE$" + msgParts[1] + "##");
                        }
                        break;
                    case "\\gadd":
                        if(msgParts.length>2){
                            this.send(ct.socketChannel, "GADD$" + msgParts[1] + "$" + msgParts[2] +  "##");
                        }
                        break;
                    case "\\gremove":
                        if(msgParts.length>2){
                            this.send(ct.socketChannel, "GREMOVE$" + msgParts[1] + "$" + msgParts[2] +  "##");
                        }
                        break;
                    case "\\logout":
                        this.send(socketChannel, "LOGOUT##");
                        break;
                    default:
                        this.send(ct.socketChannel, msg.substring(1));
                        break;
                }
                return;
            } else if (!this.currentChatIsGroup) {
                this.send(ct.socketChannel, "MSG$" + this.currentChat + "$" + msg + "##");
            } else {
                this.send(ct.socketChannel, "GMSG$" + this.currentChat + "$" + msg + "##");
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
                case "SELF":
                    if(msgParts.length>3) {
                        displaySelfMessage(msgParts[1], msgParts[2] + " : " + msgParts[3]);
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
                    if(msgParts.length==1){
                        clearGrpList();
                    } else {
                        updateGroups(msgParts[1]);
                    }
                    break;
            }
        }

        void updateUsersLater(String users){
            ArrayList<String> userList = new ArrayList<String>();
            for (String user : users.split(",")) {
                userList.add(user);
            }
            if (currentChat.equals("")) {
                currentChatIsGroup = false;
                currentChat = userList.get(0);
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

        void switchToUser(String user) {
            this.currentChat = user;
            this.currentChatIsGroup = false;
            synchronized (userChatMap) {
                ArrayList<String> msgList = (ArrayList<String>) userChatMap.get(user);
                if (msgList == null) {
                    msgList = new ArrayList<String>();
                    userChatMap.put(user, msgList);
                }
                ArrayList<String> finalMsgList = msgList;
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        updateMsgList(finalMsgList);
                    }
                });
            }
        }

        void switchToGroup(String group) {
            this.currentChat = group;
            this.currentChatIsGroup = true;
            synchronized (groupChatMap) {
                ArrayList<String> msgList = (ArrayList<String>) groupChatMap.get(group);
                if (msgList == null) {
                    msgList = new ArrayList<String>();
                    groupChatMap.put(group, msgList);
                }
                ArrayList<String> finalMsgList = msgList;
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        updateMsgList(finalMsgList);
                    }
                });
            }
        }

        void displayMessage(String sender, String msg){
            synchronized (userChatMap) {
                ArrayList<String> msgList = (ArrayList<String>) userChatMap.get(sender);
                if (msgList == null) {
                    msgList = new ArrayList<String>();
                    userChatMap.put(sender, msgList);
                }
                msgList.add(sender + " : " + msg);
                if (!currentChatIsGroup && currentChat.equals(sender)) {
                    ArrayList<String> finalMsgList = msgList;
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            updateMsgList(finalMsgList);
                        }
                    });
                }
            }
        }

        void displaySelfMessage(String sender, String msg){
            synchronized (userChatMap) {
                ArrayList<String> msgList = (ArrayList<String>) userChatMap.get(sender);
                if (msgList == null) {
                    msgList = new ArrayList<String>();
                    userChatMap.put(sender, msgList);
                }
                msgList.add(msg);
                if (!currentChatIsGroup && currentChat.equals(sender)) {
                    ArrayList<String> finalMsgList = msgList;
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            updateMsgList(finalMsgList);
                        }
                    });
                }
            }
        }

        void displayGroupMessage(String grpName, String sender, String msg){
            ArrayList<String> msgList = (ArrayList<String>) groupChatMap.get(grpName);
            if(msgList == null){
                msgList = new ArrayList<String>();
                groupChatMap.put(grpName, msgList);
            }
            msgList.add(sender + " : " + msg);
            if(currentChatIsGroup && currentChat.equals(grpName)) {
                ArrayList<String> finalMsgList = msgList;
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        updateMsgList(finalMsgList);
                    }
                });
            }
        }

        void displayInfo(String info){
            synchronized (infoChatList) {
                infoChatList.add(info);
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        updateInfoList(infoChatList);
                    }
                });
            }
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
