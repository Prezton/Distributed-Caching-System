import java.io.*;
import java.io.IOException;
import java.io.File.*;
import java.net.MalformedURLException;
import java.util.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;

public class Server extends UnicastRemoteObject implements RemoteOps{


    private static Map<String, Integer> path_version_map = new ConcurrentHashMap<String, Integer>();
    private static Map<String, Object> path_lock_map = new ConcurrentHashMap<String, Object>();
    private static String rootdir;
    private static final int chunk_size = 204800;
    private static final int huge_file_size = 10000000;

    // UnicastRemoteObject Instantiate a Server
    // listen and wait for a client
    // and create a thread for each client
    public Server() throws RemoteException {
        super();
    }

    /**
     * @brief create file on server, used for non-existed and non-directory file
     * @param path String path to create
     * @return 0 indicates create success, -1 indicates IOException
     */
    public int create_file(String path) throws RemoteException {
        String remote_path = get_remote_path(path);
        System.err.println(remote_path + " Server: create_file()");

        File file = new File(remote_path);
        try {
            file.createNewFile();
            path_version_map.put(remote_path, 1);

        } catch (IOException e) {
            System.err.println("Server: create_file() fail");
            e.printStackTrace();
            return -1;
        }
        return 0;
    }


    /**
     * @brief delete files on server
     * @param path String path to upload
     * @param sent_file file to be uploaded
     * @return the latest version of master copy
     */
    public int delete_file(String path) throws RemoteException {
        String remote_path = get_remote_path(path);
        System.err.println("Server: delete_file() " + remote_path);
        if (!path_lock_map.containsKey(remote_path)) {
            path_lock_map.put(remote_path, new Object());
        }
        
        synchronized (path_lock_map.get(remote_path)) {
            File file = new File(remote_path);
            if (!file.exists()) {
                // Errors.ENOENT;
                return -1;
            }
            try {
                boolean is_deleted = file.delete();
                if (!is_deleted) {
                    System.err.println("Server: delete_file() failed, undeleted" + is_deleted);
                }
                Object rmresult = path_version_map.remove(remote_path);
                if (rmresult == null) {
                    System.err.println("Server: delete_file() failed, unremoved" + rmresult);
                }
            } catch (SecurityException e) {
                System.err.println("Server: delete_file() Security Exception");
                e.printStackTrace();
                // Errors.EPERM
                return -2;
            }
        }

        return 0;
    }


    /**
     * @brief upload file from Proxy cache to servers
     * @param path String path to upload
     * @param sent_file file to be uploaded
     * @return the latest version of master copy
     */
    public synchronized int upload_file(String path, byte[] uploaded_file) throws RemoteException {
        String remote_path = get_remote_path(path);
        System.err.print(remote_path + " Server upload_file(), ");
        synchronized (path_lock_map.get(remote_path)) {
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
            int version = path_version_map.get(remote_path);
            System.err.println("version: " + version);
            return version;
        }

    }


    /**
     * @brief upload file from Proxy cache to servers
     * @param path String path to upload
     * @param sent_file file to be uploaded
     * @return the latest version of master copy
     */
    public synchronized int upload_huge_file(String path, byte[] uploaded_file, long offset, boolean finished) throws RemoteException {
        String remote_path = get_remote_path(path);
        System.err.print(remote_path + " Server upload_huge_file(), ");
        synchronized (path_lock_map.get(remote_path)) {
            File file = new File(remote_path);

            RandomAccessFile raf;
            try {
                raf = new RandomAccessFile(file, "rw");
                raf.seek(offset);
                raf.write(uploaded_file);
                raf.close();
            } catch (Exception e) {
                System.err.println("Server upload_file() fail");
                e.printStackTrace();
            }
            if (finished) {
                // Update version map
                if (path_version_map.containsKey(remote_path)) {
                    path_version_map.put(remote_path, path_version_map.get(remote_path) + 1);
                } else {
                    System.err.println("EMPTY VER SHOULD NOT HAPPEN ON UPLOADED FILE!");
                    path_version_map.put(remote_path, 1);
                }
                int version = path_version_map.get(remote_path);
                // System.err.println("version: " + version);
                return version;
            }

        }
        return 0;

    }


    /**
     * @brief check if path in cache
     * @param path String path to check
     * @return 0 if not in cache, otherwise version num
     */
    public Reply_FileInfo get_file_info(String path) throws RemoteException {

        String remote_path = get_remote_path(path);
        System.err.println(remote_path + " Server get_file_info()");

        // Create a lock Object to handle concurrent proxies
        if (!path_lock_map.containsKey(remote_path)) {
            path_lock_map.put(remote_path, new Object());
        }

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
        reply_fileinfo.path_valid = check_server_path(path, remote_path);

        return reply_fileinfo;
    }

    /**
     * @brief get the file from remote server
     * @param path String path of the file
     * @prerequisite path is not referred to non-exist file or directory
     * @return an array of bytes representing the file
     */
    public byte[] get_file(String path) throws RemoteException {

        String remote_path = get_remote_path(path);
        System.err.println(remote_path + " Server get_file()");

        File file = new File(remote_path);
        assert(file.exists());
        assert(!file.isDirectory());
        int file_size = (int)file.length();
        byte[] file_bytes = new byte[file_size];
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.read(file_bytes);
            raf.close();
        } catch (IOException e) {
            System.err.println("Exception in getting file from server");
            e.printStackTrace();
        }

        return file_bytes;

    }

    /**
     * @brief get the file from remote server with chunking
     * @param path String path of the file
     * @param offset file offset used for chunking
     * @prerequisite path is not referred to non-exist file or directory
     * @return an array of bytes representing the file
     */
    public byte[] get_file(String path, long offset) throws RemoteException {
        String remote_path = get_remote_path(path);
        System.err.println(remote_path + " Server get_file() for HUGE FILE with chunking!");
        File file = new File(remote_path);
        // assert(file.exists());
        // assert(!file.isDirectory());
        byte[] file_bytes;
        if (offset + chunk_size < file.length()) {
            file_bytes = new byte[chunk_size];
        } else {
            int length = (int)(file.length() - offset);
            file_bytes = new byte[length];
        }

        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.seek(offset);
            int result = raf.read(file_bytes);
            assert(result != -1);
            System.err.println("GETFILE RESULT: " + +result + "contents: " + file_bytes);
            // raf.close();
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

    private boolean check_server_path(String path, String remote_path) {
        // File file = new File(rootdir);
        // String cano_rootdir = null;
        // boolean is_valid;
        // try {
        //     cano_rootdir = file.getCanonicalPath();
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
        // System.err.println("check path: " + path + "\n" + remote_path + "\n" + cano_rootdir);

        if (path.startsWith("..") || path.startsWith("../") || path.startsWith("/..")) {
            return false;
        }
        // if (!remote_path.contains(cano_rootdir)) {
        //     return false;
        // }

        return true;
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        rootdir = args[1]; 
        System.err.println("rootdir is: " + rootdir);

        String server_name = "//" + "127.0.0.1" + ":" + port + "/peizhaolServer";
        // String server_name2 = "//" + "128.2.13.163" + ":" + 10608 + "/peizhaolServer";

        try {
            Server server = new Server();
            LocateRegistry.createRegistry(port);
            // LocateRegistry.createRegistry(10608);

            Naming.rebind(server_name, server);
            // Naming.rebind(server_name2, server);

        } catch(Exception e) {
            System.err.println("An exception in Server main!");
            e.printStackTrace();
        }


    }
}