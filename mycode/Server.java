import java.io.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;

public class Server extends UnicastRemoteObject implements RemoteOps{


    private static Map<String, Integer> path_version_map = new ConcurrentHashMap<String, Integer>();
    private static String rootdir;

    // UnicastRemoteObject Instantiate a Server
    // listen and wait for a client
    // and create a thread for each client
    public Server() throws RemoteException {
        super();
    }

    /**
     * @brief create file on server, used for non-existed and non-directory file
     * @param path String path to create
     */
    public void create_file(String path) throws RemoteException {
        System.err.println(path + " Server create_file()");
        String remote_path = get_remote_path(path);
        File file = new File(remote_path);
        try {
            file.createNewFile();
            path_version_map.put(remote_path, 1);

        } catch (IOException e) {
            System.err.println("server file.createNewFile fail");
            e.printStackTrace();
        }
    }

    /**
     * @brief upload file from Proxy cache to servers
     * @param path String path to upload
     * @param sent_file file to be uploaded
     */
    public synchronized void upload_file(String path, byte[] uploaded_file) throws RemoteException {
        System.err.println(path + " Server upload_file()");
        String remote_path = get_remote_path(path);
        File file = new File(remote_path);

        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "rw");
            raf.write(uploaded_file);
            raf.close();
        } catch (Exception e) {
            System.err.println("Server upload_file() fail");
            e.printStackTrace();
        }
        // Update version map
        if (path_version_map.containsKey(remote_path)) {
            path_version_map.put(remote_path, path_version_map.get(remote_path) + 1);
        } else {
            System.err.println("EMPTY VER SHOULD NOT HAPPEN ON UPLOADED FILE!");
            path_version_map.put(remote_path, 1);
        }

    }


    /**
     * @brief check if path in cache
     * @param path String path to check
     * @return 0 if not in cache, otherwise version num
     */
    public Reply_FileInfo get_file_info(String path) throws RemoteException {
        System.err.println(path + " Server get_file_info()");

        String remote_path = get_remote_path(path);
        File file = new File(remote_path);
        Reply_FileInfo reply_fileinfo = new Reply_FileInfo();

        // Fill in boolean parameters: exist? directory?
        boolean is_existed = file.exists();
        reply_fileinfo.is_existed = is_existed;
        boolean is_dir = file.isDirectory();
        reply_fileinfo.is_dir = is_dir;

        set_default_version_absent(remote_path);
        reply_fileinfo.version = path_version_map.get(remote_path);
        // Get version id
        if (is_existed) {
            long file_size = file.length();
            reply_fileinfo.file_size = file_size;
        } else {
            System.err.println("Server: This fileinfo is incomplete due to non-existed");
        }

        return reply_fileinfo;
    }

    /**
     * @brief get the file from remote server
     * @param path String path of the file
     * @prerequisite path is not referred to non-exist file or directory
     * @return an array of bytes representing the file
     */
    public byte[] get_file(String path) throws RemoteException {
        System.err.println(path + " Server get_file()");

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
        sb.append("/");
        sb.append(new StringBuilder(path));
        String cano_path = null;
        try {
            cano_path = (new File(sb.toString())).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return cano_path;
    }
    /**
     * @brief if the file is not in version map, set it to 1, the file must exist!!!
     * @param path String path to check
     */
    private void set_default_version_absent(String path) throws RemoteException {
        if (!path_version_map.containsKey(path)) {
            path_version_map.put(path, 1);
        }
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        rootdir = args[1]; 

        String server_name = "//" + "127.0.0.1" + ":" + port + "/peizhaolServer";
        try {
            Server server = new Server();
            LocateRegistry.createRegistry(port);
            Naming.rebind(server_name, server);

        } catch(Exception e) {
            System.err.println("An exception in Server main!");
            e.printStackTrace();
        }


    }
}