package clientInterface;

import controller.ApplicationManager;
import entities.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;

public class Client extends Thread {
    private FXDataClient client;
    private volatile ServerStatus status;
    private volatile User user;
    private volatile GameList games;
    private volatile GameStatusProperty gamestatus;
    private volatile Label lblStatus;
    private volatile ObservableList<User> users;
    private volatile UserList userlist;


    public Client(){
        //Client initiation
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost:8080/fx/")
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        client = retrofit.create(FXDataClient.class);

        status = null;
        lblStatus = null;
        games = new GameList();
        gamestatus = new GameStatusProperty();
        users = FXCollections.observableArrayList();
        userlist = new UserList();
        user = new User();

        start();
    }

    @Override
    public void run() {
        //Ensure the preloader is shown at least 1.5 seconds.
        try {
            sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Close PreloaderStage after one successful connection to the Client-Application.
        while(status == null && ApplicationManager.isRunning()) {
            try {
                update();
                Platform.runLater(ApplicationManager::openLoginStage);
                sleep(50);
            } catch (Exception e) {
                System.err.println("No client-application found.");
            }
        }

        //Update all fields while any stage is open.
        while(ApplicationManager.isRunning()){
            try {
                updateStatusLabel();
                update();
                sleep(50);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Client-application connection problems.");
                if(e instanceof IOException){
                    status.disconnected();
                }
            }
        }
    }

    public User getUser(){
        return user;
    }

    public ServerStatus getServerStatus(){
        return status;
    }

    public GameList getGames(){
        return games;
    }

    public void setServerStatusLabel(Label lblStatus){
        this.lblStatus = lblStatus;
    }

    public ObservableList<User> getUsersList(){
        return this.users;
    }

    public void sendUserData(String username, String gamepath){
        new Thread(() -> {
            User userdata = null;
            try {
                userdata = new User(new ClientSettings());
            } catch (IOException e) {
                e.printStackTrace();
            }
            userdata.setUsername(username);
            userdata.setGamepath(gamepath);
            try {
                client.sendUser(userdata).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public GameStatusProperty getGamestatusProperty(){
        return gamestatus;
    }

    public void startGame(Game game){
        new Thread(() -> {
            try {
                client.startGame(game).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void downloadGame(Game game){
        new Thread(() -> {
            try {
                client.downloadGame(game).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void openExplorer(Game game){
        new Thread(() -> {
            try {
                client.openExplorer(game).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void update() throws IOException {
        user = client.getUser().execute().body();
        //games = client.getGames().execute().body();
        if(ApplicationManager.getFocusedGame() != null){
            GameStatus newStatus = client.getGameStatus(ApplicationManager.getFocusedGame()).execute().body();
            gamestatus.downloading.setValue(newStatus.downloading);
            gamestatus.unzipping.setValue(newStatus.unzipping);
            gamestatus.download.setValue(newStatus.download);
            gamestatus.update.setValue(newStatus.update);
            gamestatus.version.setValue(newStatus.version);
            gamestatus.playable.setValue(newStatus.playable);
            gamestatus.downloadProgress.setValue(newStatus.downloadProgress);
            gamestatus.unzipProgress.setValue(newStatus.unzipProgress);
        }
        updateUsers();
        updateGames();
        status = client.getStatus().execute().body();
    }

    private void updateGames() throws IOException {
        GameList gamelist = client.getGames().execute().body();
        if(!gamelist.equals(games)){
            games = gamelist;
            Platform.runLater(ApplicationManager::updateMainstageRoot);
        }
        games = gamelist;
    }

    private void updateUsers() throws IOException {
        UserList userlist = client.getUserlist().execute().body();
        if(!userlist.equals(this.userlist)){
            userlist.remove(user);
            Platform.runLater(() -> users.setAll(userlist.toList()));
        }
        this.userlist = userlist;
    }

    private void updateStatusLabel(){
        if(lblStatus == null)
            return;
        Platform.runLater(() -> {
            if(status.isConnected())
                lblStatus.setText("Connected to server: " + status.getServerIP());
            else
                lblStatus.setText("Waiting for server connection.");
        });
    }

}
