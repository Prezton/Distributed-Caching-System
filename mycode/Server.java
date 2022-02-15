import java.io.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

public class Server extends UnicastRemoteObject implements RemoteOps{
    // UnicastRemoteObject Instantiate a Server
    // listen and wait for a client
    // and create a thread for each client
    public Server() throws RemoteException {
        super(0);
    }


    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        String rootdir = args[1]; 
        Server server = null;

        String server_name = "peizhaolServer";
        try {
            LocateRegistry.createRegistry(port);

        } catch(RemoteException e1) {
            System.err.println("createRegistry incurred an exception in Server main");
            e1.printStackTrace();
        }

        try {
            server = new Server();
        } catch (RemoteException e) {
            System.err.println("Server instantiate incurred an exception in Server main");
            e.printStackTrace();
        }

        try {
            if (server == null) {
                System.err.println("Server instantiate failed: null server");
            }
            Naming.rebind(server_name, server);
        } catch (MalformedURLException e) {
            System.err.println("rebind incurred an exception in Server main");
            e.printStackTrace();
        } catch (RemoteException e2) {
            System.err.println("rebind incurred an exception in Server main");
            e2.printStackTrace();

        }


    }
}