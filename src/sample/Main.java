package sample;

import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;

public class Main extends Application {
    ListView<String> chatList = new ListView<String>();
    ListView<String> msgList = new ListView<String>();
    String currentChat = new String();

    @Override
    public void start(Stage primaryStage) throws Exception{
        Group root = new Group();
        VBox vPane = new VBox();
        HBox hPane1 = new HBox();
        hPane1.setPadding(new Insets(10));
        hPane1.setSpacing(10);
        HBox hPane2 = new HBox();
        hPane2.setPadding(new Insets(10));
        hPane2.setSpacing(10);

        this.chatList.setPrefSize(150,430);
        this.msgList.setPrefSize(450, 430);

        hPane1.getChildren().addAll(this.chatList,this.msgList);
        TextField textField = new TextField();
        textField.setPrefWidth(500);
        textField.setPromptText("enter your message");
        Button sendButton = new Button();
        sendButton.setPrefSize(100,10);
        sendButton.setText("Login");
        sendButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
            }
        });
        hPane2.getChildren().addAll(textField, sendButton);
        vPane.getChildren().addAll(hPane1,hPane2);

        root.getChildren().add(vPane);
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 630, 500));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
