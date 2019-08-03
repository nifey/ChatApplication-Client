package sample;

import javafx.application.Application;
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
import sample.ClientThread;


public class Main extends Application {
    ListView<String> userList = new ListView<String>();
    ListView<String> msgList = new ListView<String>();
    ListView<String> grpList = new ListView<String>();
    String currentChat = new String();
    Boolean currentChatIsGroup = false;
    ClientThread ct = new ClientThread("localhost", 12121, msgList.getItems(),userList.getItems(), grpList.getItems());

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
}
