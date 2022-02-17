/* Sample skeleton for proxy */

import java.io.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;

class Proxy {

    /**
     * map between fd and RandomAccessFile Object
     */
    private static Map<Integer, FileInfo> fd_file_map = new HashMap<Integer, FileInfo>();
    private static int fd_count = 0;
    private static final Object lock = new Object();
    // Special lock used for cache operations
    private static final Object cache_lock = new Object();
    private static String server_name;
    private static RemoteOps srv;
    private static String cache_dir;
    private static int cache_size;
    private static Cache cache;

    private static class FileHandler implements FileHandling {

        public synchronized int open( String path, OpenOption o ) {
            System.err.print( path + " open, mode: ");
            // Invalid path argument
            if (path == null || path.equals("")) {
                return Errors.EINVAL;
            }
            Reply_FileInfo reply_fileinfo = null;
            try {
                reply_fileinfo = srv.get_file_info(path);
            } catch (RemoteException e) {
                System.err.println("Exception on getting remote file info");
                e.printStackTrace();
            }

            String access_mode;
            boolean check_existed = false;
            if (o == OpenOption.CREATE) {
                access_mode = "rw";
                System.err.println("CREATE");
            } else if (o == OpenOption.CREATE_NEW) {
                access_mode = "rw";
                check_existed = true;
                System.err.println("CREATE NEW");

            } else if (o == OpenOption.READ) {
                access_mode = "r";
                System.err.println("READ");

            } else if (o == OpenOption.WRITE) {
                access_mode = "rw";
                System.err.println("WRITE");
            } else {
                return Errors.EINVAL;
            }

            // boolean is_dir = file.isDirectory();
            
            // Set a series of flags according to Server returned infomation
            boolean is_dir = reply_fileinfo.is_dir;
            boolean is_existed = reply_fileinfo.is_existed;
            int remote_version_num = reply_fileinfo.version;

            if (is_existed) {
                if (check_existed) {
                    return Errors.EEXIST;
                }
                if (is_dir) {
                    if (o != OpenOption.READ) {
                        return Errors.EISDIR;
                    }
                }
            } else {
                if ((o == OpenOption.READ) || (o == OpenOption.WRITE)) {
                    return Errors.ENOENT;
                }
                // file does not exist on server and mode is create or create_new, Server needs to create file
                if (!is_dir) {
                    try {
                        System.err.println("File not on server, create it! now remote ver num is: " + remote_version_num);
                        srv.create_file(path);
                    } catch (RemoteException e) {
                        System.err.println("srv.create_file(path) fail");
                        e.printStackTrace();
                    }
                }
            }

            RandomAccessFile raf;
            FileInfo fileinfo = null;
            // Instantiate a file corresponding to cache_path
            String cache_path = get_cache_path(path);

            try {
                if (!is_dir) {
                    int local_version = cache.get_local_version(cache_path);
                    // version validation, check local and remote version diff
                    if (local_version != remote_version_num) {
                        System.err.println(path + " Getting from remote server!");
                        // Get from remote server if local version does not match or local has no correct file
                        byte[] received_file = fetch_file(path);
                        // Save file to cache_dir
                        save_file_locally(cache_path, received_file);
                        // Save to local cache
                        raf = new RandomAccessFile(cache_path, access_mode);
                        fileinfo = new FileInfo();
                        fileinfo.raf = raf;
                        fileinfo.is_existed = is_existed;
                        fileinfo.is_dir = is_dir;
                        fileinfo.version = remote_version_num;
                        fileinfo.path = cache_path;
                        // Store information in local cache
                        cache.add_to_cacheline(fileinfo);
                    } else {
                        System.err.println(path + " Getting from local cache!");
                        // Get from local cache
                        fileinfo = cache.get_local_file_info(cache_path);
                        raf = new RandomAccessFile(fileinfo.path, access_mode);
                        fileinfo.raf = raf;
                    }
                } else {
                    fileinfo.raf = null;
                }
            } catch (FileNotFoundException e) {
                System.err.println("exception in open: RandomAccessFile(file, access_mode)");
                e.printStackTrace();
                return Errors.ENOENT;
            }




            int intFD;
            synchronized (lock) {
                fd_count += 1;
                intFD = fd_count;
                assert(!fd_file_map.containsKey(intFD));
            }

            fd_file_map.put(intFD, fileinfo);

            return intFD;
        }

        public synchronized int close( int fd ) {
             System.err.println("close");
            if (!fd_file_map.containsKey(fd)) {
                return Errors.EBADF;
            }

            FileInfo fileinfo = fd_file_map.get(fd);
            RandomAccessFile raf;
            if (!fileinfo.is_dir) {
                raf = fileinfo.raf;
            } else {
                fd_file_map.remove(fd);
                return 0;
            }
            if (raf.equals(null)) {
                System.err.println("close: null raf! STH WRONG!");
                if (fd_file_map.containsKey(fd)) {
                    fd_file_map.remove(fd);
                    return 0;
                }

            }
            try {
                raf.close();
                fd_file_map.remove(fd);
            } catch (IOException e) {
                System.err.println("exception in close: raf.close");
                e.printStackTrace();
                return -1;
            }
            return 0;
        }

        public synchronized long write( int fd, byte[] buf ) {
            System.err.println("write");

            if (!fd_file_map.containsKey(fd)) {
                return Errors.EBADF;
            }

            if (buf == null) {
                return Errors.EINVAL;
            }
            

            FileInfo fileinfo = fd_file_map.get(fd);
            if (fileinfo.is_dir) {
                return Errors.EISDIR;
            }
            RandomAccessFile raf = fileinfo.raf;

            try {
                raf.write(buf);
            } catch (IOException e) {
                System.err.println("Proxy: Exception in write");
                e.printStackTrace();
                return Errors.EBADF;
            }
            return buf.length;

        }

        public long read( int fd, byte[] buf ) {
            System.err.println("read");
            if (!fd_file_map.containsKey(fd)) {
                return Errors.EBADF;
            }

            if (buf == null) {
                return Errors.EINVAL;
            }
            FileInfo fileinfo = fd_file_map.get(fd);
            if (fileinfo.is_dir) {
                return Errors.EISDIR;
            }
            RandomAccessFile raf = fileinfo.raf;
            try {
                long length = (long) raf.read(buf);
                if (length == -1) {
                    // reach end of file
                    return 0;
                }
                return length;
            } catch (IOException e) {
                System.err.println("err in raf.read");
                e.printStackTrace();
                return Errors.EBADF;
            }
            

        }

        public long lseek( int fd, long pos, LseekOption o ) {
            System.err.println("lseek");

            if (!fd_file_map.containsKey(fd)) {
                return Errors.EBADF;
            }
            if (pos < 0) {
                return Errors.EINVAL;
            }

            FileInfo fileinfo = fd_file_map.get(fd);
            RandomAccessFile raf = fileinfo.raf;
            if (fileinfo.is_dir) {
                return Errors.EISDIR;
            }

            long new_pos = pos;
            if (o == LseekOption.FROM_CURRENT) {
                try {
                    long current_offset = raf.getFilePointer();
                    new_pos = pos + current_offset;
                    raf.seek(new_pos);
                } catch (IOException e) {
                    // question about those IOExceptions!!!
                    System.err.println("err in raf.seek");
                    e.printStackTrace();
                    return Errors.EINVAL;
                }

            } else if (o == LseekOption.FROM_END) {
                try {
                    long length = raf.length();
                    new_pos = length - pos;
                    raf.seek(new_pos);
                } catch (IOException e) {
                    System.err.println("err in raf.seek");
                    e.printStackTrace();
                    return Errors.EINVAL;
                }
            } else if (o == LseekOption.FROM_START) {
                try {
                    new_pos = pos;
                    raf.seek(new_pos);
                } catch (IOException e) {
                    System.err.println("err in raf.seek");
                    e.printStackTrace();
                    return Errors.EINVAL;
                }
            }
            return new_pos;
        }

        public int unlink( String path ) {
            System.err.println("unlink");

            if (path == null) {
                return Errors.EINVAL;
            }
            File file = new File(path);
            if (!file.exists()) {
                return Errors.ENOENT;
            }

            try {
                file.delete();
                return 0;
            } catch (SecurityException e) {
                System.err.println("err in file.delete()");
                e.printStackTrace();
                return Errors.EPERM;
            }
            
        }

        public void clientdone() {
            System.err.println("Client Done!!!");
            return;
        }

        /**
        * @brief Get file from server
        * @param path remote file path on server
        * @return an array of file bytes
        */
        private byte[] fetch_file(String path) {
            byte[] received_file = null;
            try {
                received_file = srv.get_file(path);
            } catch (RemoteException e) {
                System.err.println("Proxy: fet_file Exception");
                e.printStackTrace();
            }
            return received_file;
        }

        /**
        * @brief create file on local cache directory
        * @param cache_path path to store the file
        * @param received_file received_file from server
        */
        private void save_file_locally(String cache_path, byte[] received_file) {
            RandomAccessFile tmp;
            try {
                tmp = new RandomAccessFile(cache_path, "rw");
                tmp.write(received_file);
                tmp.close();
            } catch (IOException e) {
                System.err.println("Proxy: save_file_locally exception");
                e.printStackTrace();
            }
        }

    }
    
    private static class FileHandlingFactory implements FileHandlingMaking {
        public FileHandling newclient() {
            return new FileHandler();
        }
    }

    private static String get_cache_path(String path) {
        StringBuilder sb = new StringBuilder(cache_dir);
        sb.append("/");
        sb.append(new StringBuilder(path));
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        // System.out.println("Hello World");

        String serverip = args[0];
        int port = Integer.parseInt(args[1]);
        cache_dir = args[2];
        cache_size = Integer.parseInt(args[3]);

        server_name = "//" + serverip + ":" + port + "/peizhaolServer";
        cache = new Cache(cache_dir, cache_size);
        try {
            // Look up reference in registry
            srv = (RemoteOps) Naming.lookup(server_name);
        } catch (NotBoundException e) {
            System.err.println("Exception in Proxy main");
            e.printStackTrace();
        }

        (new RPCreceiver(new FileHandlingFactory())).run();
    }
}