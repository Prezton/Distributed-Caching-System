import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;

public class Server extends UnicastRemoteObject{
    // UnicastRemoteObject Instantiate a Server
    // listen and wait for a client
    // and create a thread for each client
    public Server() throws RemoteException {
        super(0);
    }


    public static void main(String[] args) {

    }
}