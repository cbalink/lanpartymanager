package server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import entities.Game;
import entities.User;
import helper.NetworkClassRegistrationHelper;
import helper.PropertiesHelper;
import messages.GamesizeMessage;
import requests.DownloadRequest;
import requests.GamelistRequest;
import requests.GamesizeRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * {@link Server} class for the lan-server. It sets up the whole {@link Server} and registers all needed classes for
 * the communication with the <code>clients</code>. It creates and stores the <code>game-list and user-list</code>.
 * Also it registers all needed <code>listener</code> for the {@link Server}.
 * <p>
 * Any changes at the <code>settings.properties</code> file need a {@link Server} restart. In this case use the
 * {@link #rebuild()} method provided by the {@link Server} class.
 *
 * @see com.esotericsoftware.kryonet.Server
 */
public final class Server {

    private static Server serverObj = null;

    /**
     * Setup the game-streaming server for a lan-party, if none is running.
     * <p>
     * {@link #start()} call required.
     *
     * @return the running {@link Server} object.
     */
    public static Server build(){
        if(serverObj == null){
            serverObj = new Server();
        }
        return serverObj;
    }
    /**
     * Rebuilds the Server.
     * <p>
     * {@link #start()} call required.
     *
     * @throws NullPointerException no server is running.
     * @return a new initialized {@link Server} object.
     */
    public static Server rebuild(){
        if(serverObj != null){
            serverObj = new Server();
            return serverObj;
        }
        throw new NullPointerException("No server running.");
    }
    /**
     * Save stop for the {@link Server}. Closing any connections and stop the running {@link Server}.
     */
    public static void stop(){
        serverObj.server.close();
        serverObj.server.stop();
        serverObj.gamelist = null;
        serverObj = null;
    }

    /**
     * {@link com.esotericsoftware.kryonet.Server} object used for the server base functionality.
     */
    private com.esotericsoftware.kryonet.Server server;
    /**
     * <code>Game-list</code> with all available games on the {@link Server}.
     */
    private ArrayList<Game> gamelist;
    /**
     * <code>User-list</code> with all connected users on the {@link Server}.
     */
    private HashMap<Integer, User> userlist;
    private String gamepath;

    /**
     * Constructs a new {@link Server} object.
     * Also registering classes and adding the needed listener for the game-streaming server.
     * <p>
     * {@link #start()} call required.
     *
     * @see com.esotericsoftware.kryonet.Server
     */
    private Server(){
        //Initialize the Server-Object
        server = new com.esotericsoftware.kryonet.Server();

        //Register classes needed for communication
        NetworkClassRegistrationHelper.registerClasses(server);

        //Bind the ports for the server
        int tcp = Integer.valueOf(PropertiesHelper.getServerTcp());
        int udp = Integer.valueOf(PropertiesHelper.getServerUdp());
        try {
            server.bind(tcp, udp);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //register the needed listener to handle the incoming client-requests
        this.registerListener();

        //build the game-list
        gamelist = GameListBuilder.build();

        //initialize user-list
        userlist = new HashMap<>();

        gamepath = PropertiesHelper.getProperties("server.properties").getProperty("gamepath");
    }
    /**
     * Starts the {@link Server}.
     */
    public void start(){
        server.start();
    }
    /**
     * Registers the needed <code>listener</code> for the {@link Server} to listen to incoming <code>client</code>
     * requests.
     *
     * @see com.esotericsoftware.kryonet.Server
     */
    private void registerListener(){
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                connection.sendTCP(gamelist);
            }
            @Override
            public void disconnected(Connection connection) {
                userlist.remove(connection.getID());
                server.sendToAllTCP(userlist);
            }
            @Override
            public void received(Connection connection, Object object){
                if(object instanceof GamelistRequest){
                    connection.sendTCP(gamelist);
                    System.out.println("User: " + userlist.get(connection.getID()).toString() + " has requested gamelist.");
                }
                if(object instanceof User){
                    User user = (User)object;
                    userlist.put(connection.getID(), user);
                    server.sendToAllTCP(userlist);
                    System.out.println("User: " + userlist.get(connection.getID()).toString() + " has connected.");
                }
                if(object instanceof DownloadRequest){
                    DownloadRequest dRequest = (DownloadRequest)object;
                    String ip = connection.getRemoteAddressTCP().getAddress().getHostAddress();
                    String filePath = PropertiesHelper.getProperties("server.properties").getProperty("gamepath")+
                            dRequest.game.getProperties().getProperty("file");
                    FileClient fc = new FileClient(ip, dRequest.port, filePath);
                }
                if(object instanceof GamesizeRequest){
                    GamesizeRequest gsRequest = (GamesizeRequest)object;
                    String filePath = PropertiesHelper.getProperties("server.properties").getProperty("gamepath")+
                            gsRequest.game.getProperties().getProperty("file");
                    File gFile = new File(filePath);
                    connection.sendTCP(new GamesizeMessage(gsRequest.game, gFile.length()));
                }
            }

        });
    }
    /**
     * @return <code>game-list</code> of the {@link Server}.
     */
    public ArrayList<Game> getGamelist(){
        return this.gamelist;
    }
    /**
     * Refreshes <code>game-list</code> of the {@link Server} and send the new <code>game-list</code> to all connected
     * <code>clients</code>.
     */
    public void refreshGamelist(){
        gamelist = GameListBuilder.build();
        server.sendToAllTCP(gamelist);
    }
}
