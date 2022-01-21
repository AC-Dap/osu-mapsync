package org.acdap.osusynchro;

import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.acdap.osusynchro.network.NetworkManager;
import org.acdap.osusynchro.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {

    LocalBeatmapSource localSource;
    RemoteBeatmapSource remoteSource;

    NetworkManager network;

    Stage primaryStage;
    Scene primaryScene;

    @Override
    public void start(Stage stage){
        primaryStage = stage;

        stage.setTitle("osu!mapsync");
        stage.setWidth(900);
        stage.setHeight(600);

        localSource = new LocalBeatmapSource();
        remoteSource = new RemoteBeatmapSource();

        network = new NetworkManager(this, localSource, remoteSource);
        remoteSource.updateNetwork(network);

        Node local = getLocalUI();
        Node remote = getRemoteUI();
        HBox sourceRoot = new HBox(local, new Separator(Orientation.VERTICAL), remote);
        HBox.setHgrow(local, Priority.ALWAYS);
        HBox.setHgrow(remote, Priority.ALWAYS);
        sourceRoot.setId("source-root");

        Button syncBtn = new Button("Sync!");
        syncBtn.setMaxWidth(Double.MAX_VALUE);

        syncBtn.setOnAction(e -> {
            synchronized (localSource.getBeatmaps()){
                synchronized (remoteSource.getBeatmaps()){
                    ArrayList<Beatmap> bmsToSend = FileManager.getMissingBeatmaps(localSource.getBeatmaps(), remoteSource.getBeatmaps());
                    try {
                        Path zip = FileManager.zipBeatmaps(Paths.get(localSource.beatmapSourcePath), bmsToSend);
                        System.out.println("New zip created: " + zip);
                    } catch (IOException ex) {
                        System.out.println("Error zipping beatmaps.");
                        ex.printStackTrace();
                    }
                }
            }
        });

        VBox root = new VBox(sourceRoot, syncBtn);

        primaryScene = new Scene(root);
        primaryScene.getStylesheets().add("css/main.css");
        stage.setScene(primaryScene);

        stage.show();
    }

    public boolean confirmIncomingConnection(String addr){
        ((TextField) primaryScene.lookup("#remote-ip-field")).textProperty().set(addr);
        System.out.println(addr);

        return true;
    }

    private Node getLocalUI() {
        // UI elements
        Label fileDialogLabel = new Label("Choose your osu! song's directory to sync with:");

        TextField filePathField = new TextField();
        Button fileSelectBtn = new Button("Browse");
        HBox fileSelect = new HBox(filePathField, fileSelectBtn);
        HBox.setHgrow(filePathField, Priority.ALWAYS);

        Node beatmapList = getBeatmapListingUI(localSource);

        // Event handling

        // File select button
        DirectoryChooser dirChooser = new DirectoryChooser();
        fileSelectBtn.setOnAction(e -> {
            File dir = dirChooser.showDialog(primaryStage);
            if(dir != null){
                filePathField.setText(dir.getAbsolutePath());
            }
        });

        // Update source whenever path field changes
        filePathField.textProperty().addListener((obs, oldVal, newVal) -> {
            localSource.beatmapSourcePath = newVal;
        });

        VBox root = new VBox(
                fileDialogLabel,
                fileSelect,
                beatmapList
        );
        root.setId("local-root");
        root.setPrefWidth(primaryStage.widthProperty().get() / 2);
        return root;
    }

    private Node getRemoteUI() {
        // UI elements
        Label remoteSelectLabel = new Label("Enter the IP of the remote computer to sync with:");

        TextField remoteField = new TextField();
        remoteField.setId("remote-ip-field");
        Button remoteConnect = new Button("Connect");
        HBox remoteSelect = new HBox(remoteField, remoteConnect);
        HBox.setHgrow(remoteField, Priority.ALWAYS);

        Node beatmapList = getBeatmapListingUI(remoteSource);

        // Event handling

        // Connect and get beatmaps from remote source when we try and connect
        remoteConnect.setOnAction(e -> {
            String addr = remoteField.textProperty().get();
            try{
                network.startClientConnection(addr);
            } catch (IOException ex) {
                System.out.println("Invalid address " + addr);
            }
            remoteSource.refreshBeatmaps();
        });

        VBox root = new VBox(
                remoteSelectLabel,
                remoteSelect,
                beatmapList
        );
        root.setId("remote-root");
        root.setPrefWidth(primaryStage.widthProperty().get() / 2);
        return root;
    }

    private Node getBeatmapListingUI(BeatmapSource source){
        ScrollPane listScrollPane = new ScrollPane();
        listScrollPane.fitToWidthProperty().set(true);
        listScrollPane.getStyleClass().add("map-list-scrollpane");
        VBox scrollContent = new VBox();
        listScrollPane.setContent(scrollContent);

        Label listInfo = new Label();

        Button loadMaps = new Button("Read maps");
        Button toggleMap = new Button("Exclude this map");
        Region spacing = new Region();
        HBox btnContainer = new HBox(loadMaps, spacing, toggleMap);
        HBox.setHgrow(spacing, Priority.ALWAYS);


        // Event handling

        // Toggle group for all the map btns
        ToggleGroup mapBtnGroup = new ToggleGroup();
        // ignored property for the current selected btn
        SimpleBooleanProperty currentSelectedIgnored = new SimpleBooleanProperty(false);

        // Refresh our list of beatmaps
        loadMaps.setOnAction(e -> {
            boolean success = source.refreshBeatmaps();

            // Error when loading beatmaps
            if(!success){
                listInfo.setText("ERROR: Unable to read beatmaps.");
                scrollContent.getChildren().clear();
            }
        });

        // Get the currently selected button, and toggle the corresponding
        // beatmap to the corresponding state
        toggleMap.setOnAction(e -> {
            ToggleButton currentBtn = (ToggleButton) mapBtnGroup.getSelectedToggle();
            if(currentBtn == null) return;

            int i = (int) currentBtn.getProperties().get("map-btn-index");
            boolean ignore = (boolean) currentBtn.getProperties().get("map-btn-ignore");
            source.setBeatmapIgnore(i, !ignore);
        });

        // When beatmap list is refreshed, create a toggle button for each map
        source.addBeatmapRefreshedListener(() -> {
            List<Beatmap> beatmaps = source.getBeatmaps();
            if(beatmaps == null) return;

            ArrayList<ToggleButton> btns = new ArrayList<>();
            synchronized (beatmaps){
                for (int i = 0; i < beatmaps.size(); i++) {
                    Beatmap bm = beatmaps.get(i);
                    ToggleButton btn = new ToggleButton(bm.name());
                    btn.setMaxWidth(Double.MAX_VALUE);
                    btn.setToggleGroup(mapBtnGroup);

                    btn.getProperties().put("map-btn-ignore", false);
                    btn.getProperties().put("map-btn-index", i);

                    // Update currentSelectedIgnored when we select a new button
                    btn.setOnAction(ev -> {
                        boolean ignore = (boolean) btn.getProperties().get("map-btn-ignore");
                        currentSelectedIgnored.set(ignore);
                    });

                    btns.add(btn);
                }
            }

            currentSelectedIgnored.set(false);
            scrollContent.getChildren().setAll(btns);
            listInfo.setText(beatmaps.size() + " maps found.");
        });

        // Update the ignored property of the corresponding button
        source.addBeatmapIgnoreListener((i, ignore) -> {
            for(Node btn : scrollContent.getChildren()){
                if((int) btn.getProperties().get("map-btn-index") != i) continue;

                btn.getProperties().put("map-btn-ignore", ignore);
                if(ignore){
                    btn.getStyleClass().add("map-btn-ignore");
                }else{
                    btn.getStyleClass().remove("map-btn-ignore");
                }

                if(btn.equals(mapBtnGroup.getSelectedToggle())){
                    currentSelectedIgnored.set(ignore);
                }
                return;
            }
        });

        // Update the toggleMap button text depending on the state of the
        // current selected button
        currentSelectedIgnored.addListener((observableValue, oldValue, newValue) -> {
            toggleMap.setText(newValue ? "Include this map" : "Exclude this map");
        });

        VBox root = new VBox(
                listScrollPane,
                listInfo,
                btnContainer
        );
        root.getStyleClass().add("map-list-root");
        return root;
    }

    @Override
    public void stop(){
        network.close();
    }
}
