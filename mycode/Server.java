import java.io.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

public class Server extends UnicastRemoteObject implements RemoteOps{


    private static Map<String, Integer> path_version_map = new HashMap<String, Integer>();
    private static String rootdir;

    // UnicastRemoteObject Instantiate a Server
    // listen and wait for a client
    // and create a thread for each client
    public Server() throws RemoteException {
        super(0);
    }


    /**
     * @brief check if path in cache
     * @param[in] path String path to check
     * @return 0 if not in cache, otherwise version num
     */
    public Reply_FileInfo get_file_info(String path) throws RemoteException {
        String remote_path = get_remote_path(path);
        File file = new File(remote_path);
        Reply_FileInfo reply_fileinfo = new Reply_FileInfo();

        // Fill in boolean parameters: exist? directory?
        boolean is_existed = file.exists();
        reply_fileinfo.is_existed = is_existed;
        boolean is_dir = file.isDirectory();
        reply_fileinfo.is_dir = is_dir;
        // Get version id
        if (is_existed) {
            set_default_version_absent(path);
            reply_fileinfo.version = path_version_map.get(path);
            long file_size = file.length();
            reply_fileinfo.file_size = file_size;
        }

        return reply_fileinfo;
    }

    public byte[] get_file(String path) throws RemoteException {
        String remote_path = get_remote_path(path);
        File file = new File(remote_path);
        assert(file.exists());
        assert(!file.isDirectory());
        int file_size = (int)file.length();
        byte[] file_bytes = new byte[file_size];
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.read(file_bytes);
        } catch (IOException e) {
            System.err.println("Exception in getting file from server");
            e.printStackTrace();
        }

        return file_bytes;

    }

    private static String get_remote_path(String path) {
        StringBuilder sb = new StringBuilder(rootdir);
        sb.append(new StringBuilder(path));
        return sb.toString();
    }
    /**
     * @brief if the file is not in version map, set it to 1, the file must exist!!!
     * @param[in] path String path to check
     */
    private void set_default_version_absent(String path) throws RemoteException {
        if (!path_version_map.containsKey(path)) {
            path_version_map.put(path, 1);
        }
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        rootdir = args[1]; 
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