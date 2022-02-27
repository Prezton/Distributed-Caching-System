/* Sample skeleton for proxy */

import java.io.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;

import javax.print.DocFlavor.INPUT_STREAM;

import java.nio.file.Files;
import java.io.File;

class Proxy {

    /**
     * map between fd and RandomAccessFile Object
     */
    private static Map<Integer, FileInfo> fd_file_map = new ConcurrentHashMap<Integer, FileInfo>();
    private static int fd_count = 0;
    private static final Object lock = new Object();
    // Special lock used for cache operations
    private static final Object cache_lock = new Object();
    private static String server_name;
    private static RemoteOps srv;
    private static String cache_dir;
    private static int cache_size;
    private static Cache cache;
    private static final int chunk_size = 204800;

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

            // If remote path is outside the root dir
            if (!reply_fileinfo.path_valid) {
                System.err.println("Remote path outside server rootdir");
                return Errors.EPERM;
            }


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
            int remote_file_size = (int)reply_fileinfo.file_size;

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
                        int create_result = srv.create_file(path);
                        if (create_result == -1) {
                            // Create file fail, usually because the file is under a non-existed
                            return Errors.ENOENT;
                        }
                    } catch (RemoteException e) {
                        System.err.println("Proxy: srv.create_file(path) fail");
                        e.printStackTrace();
                    }
                }
            }

            int intFD;
            synchronized (lock) {
                fd_count += 1;
                intFD = fd_count;
                assert(!fd_file_map.containsKey(intFD));
            }
            
            RandomAccessFile raf = null;
            FileInfo fileinfo;
            // Instantiate a file corresponding to cache_path
            String cache_path = get_cache_path(path) + "_rdonly_" + remote_version_num;
            fileinfo = set_fileinfo_value(is_existed, is_dir, remote_version_num, remote_file_size, cache_path, path, raf);

            if (!is_dir) {
                int deal_result;
                synchronized (cache_lock) {
                    deal_result = deal(fileinfo, raf, access_mode, intFD);
                }
                if (deal_result == -1) {
                    return Errors.ENOENT;
                } else if (deal_result == -2) {
                    return Errors.ENOMEM;
                }
            } else {
                fileinfo = set_fileinfo_value(is_existed, is_dir, remote_version_num, remote_file_size, cache_path, path, null);
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
                System.err.println("Proxy close() null raf! STH WRONG!");
                if (fd_file_map.containsKey(fd)) {
                    fd_file_map.remove(fd);
                    return 0;
                }

            }
            // File has been overwritten
            if (fileinfo.access_mode.equals("rw")) {
                try {
                    long new_length = raf.length();
                    byte[] sent_file = new byte[(int)new_length];
                    raf.seek(0);
                    int result = raf.read(sent_file);
                    assert(result != -1);
                    int new_version = srv.upload_file(fileinfo.orig_path, sent_file);
                    synchronized (cache_lock) {
                        cache.decrease_reference_count(fileinfo.write_path);
                        if (cache.update_file_and_version(fileinfo, new_version, (int)new_length) == -2) {
                            return Errors.ENOMEM;
                        }
                        cache.traverse_cache();
                    }
                } catch (Exception e) {
                    System.err.println("Proxy close(), sent file failed");
                    e.printStackTrace();
                }
            } else {
                synchronized (cache_lock) {
                    // Move to end to deal with slow reads LRU!
                    cache.move_to_end(fileinfo.path);
                    cache.decrease_reference_count(fileinfo.path);
                    cache.traverse_cache();
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
            System.err.println("Proxy: write, fd:" + fd);

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
            System.err.println("Proxy: read, fd:" + fd);
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
            System.err.println("Proxy: unlink()" + path);

            if (path == null || path.startsWith("..")) {
                return Errors.EINVAL;
            }
            Reply_FileInfo reply_fileinfo = null;
            try {
                reply_fileinfo = srv.get_file_info(path);
            } catch (RemoteException e) {
                System.err.println("Proxy: unlink() remote exception");
                e.printStackTrace();
            }
            boolean is_existed = reply_fileinfo.is_existed;
            int remote_version = reply_fileinfo.version;
            String cache_path = get_cache_path(path) + "_rdonly_" + remote_version;

            // if (!is_existed) {
            //     return Errors.ENOENT;
            // }

            // if (reply_fileinfo.is_dir) {
            //     return Errors.EISDIR;
            // }

            // If file is outside server root directory
            if (!reply_fileinfo.path_valid) {
                System.err.println("Proxy: unlink() Remote path outside server rootdir");
                return Errors.EPERM;
            }

            File file = new File(cache_path);
            try {
                // Delete local cached copy (all versions) and if latest ver exists, also delete it
                synchronized(cache_lock) {
                    cache.delete_old_versions(path, remote_version);
                    if (file.exists()) {
                        if (cache.contains_file(cache_path)) {
                            CachedFileInfo cached_fileinfo = cache.get_local_file_info(cache_path);
                            if (cached_fileinfo.reference_count <= 0) {
                                boolean is_removed = cache.remove_file(cache_path);
                                if (!is_removed) {
                                    System.err.println("Proxy: unlink() failed to remove latest version");
                                }
                            }
                        }
                    }
                }

                // Delete server's master copy and get return number
                int delete_result = srv.delete_file(path);
                if (delete_result == -1) {
                    return Errors.ENOENT;
                } else if (delete_result == -2) {
                    return Errors.EPERM;
                }
                return 0;
            } catch (Exception e) {
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
        * @brief Deal with 3 cases: 1. File not in cache 2. File in cache but not latest 3. File in cache and valid.
        * @param fileinfo fileinfo, prefilled with remote reply information
        * @param raf randomaccessfile for read and write, stored in map at end of this method
        * @param access_mode access_mode
        * @return an array of file bytes
        */
        private int deal(FileInfo fileinfo, RandomAccessFile raf, String access_mode, int fd) {
            System.err.println("Proxy: deal()");
            String path = fileinfo.orig_path;
            String cache_path = fileinfo.path;
            int remote_version_num = fileinfo.version;
            int remote_file_size = fileinfo.file_size;
            int local_version;
            boolean in_cache;
            in_cache = cache.contains_file(cache_path);
            
            // version validation, check local and remote version diff
            if ((!in_cache)) {
                System.err.println(path + " Getting from remote server!");
                // Get from remote server if local version does not match or local has no correct file
                if (remote_file_size > cache_size) {
                    // return Errors.ENOMEM;
                    return -2;
                }
                
                System.err.println("remain size: " + cache.get_cache_remain_size() + " current file size: " + remote_file_size);
                // Delete all old versions in the cache, use orig_path + latest version to iterate through stale files
                cache.delete_old_versions(path, remote_version_num);
                
                if (cache.get_cache_remain_size() < remote_file_size) {
                    boolean is_enough = cache.evict(remote_file_size);
                    if (!is_enough) {
                        // return Errors.ENOMEM;
                        return -2;
                    }
                }

                


                // If the file is on server, just fetch it.
                byte[] received_file = fetch_file(path);
                // Save file to cache_dir
                save_file_locally(cache_path, received_file);

                cache.add_to_cacheline(new CachedFileInfo(fileinfo));
                
            } else {
                System.err.println("Getting from local cache!");
                // Get from local cache
                CachedFileInfo cached_fileinfo;
                cached_fileinfo = cache.get_local_file_info(cache_path);
                if (cached_fileinfo != null) {
                    cache.move_to_end(cached_fileinfo);

                } else {
                    System.err.println("Proxy: get_local_file_info() failed");
                }
                
            }

            // Create write copy for non-read mode
            if (!access_mode.equals("r")) {

                // Add reference count to read file to avoid it being evicted before making write copy.
                if (!cache.add_reference_count(cache_path)) {
                    System.err.println("Reference Count Add for Making Write Copy Failed! " + cache_path);
                }
                int create_result = create_write_copy(fileinfo, fd);

                // Decrease reference count to read file to avoid it being evicted before making write copy.
                if (!cache.decrease_reference_count(cache_path)) {
                    System.err.println("Reference Count Decrease for Making Write Copy Failed! " + cache_path);
                }
                if (create_result != 0) {
                    return create_result;
                }
                // Save to local cache
                String write_path = get_cache_path(fileinfo.orig_path) + "_wr_" + fd;
                try {
                    raf = new RandomAccessFile(write_path, access_mode);

                } catch (FileNotFoundException e) {
                    System.err.println("exception in open: RandomAccessFile(file, access_mode)");
                    e.printStackTrace();
                    // return Errors.ENOENT;
                    return -1;
                }
                fileinfo.raf = raf;
                if (!cache.add_reference_count(write_path)) {
                    System.err.println("Reference Count Add Failed! " + write_path);
                }
            } else {
                try {
                    raf = new RandomAccessFile(cache_path, access_mode);

                } catch (FileNotFoundException e) {
                    System.err.println("exception in open: RandomAccessFile(file, access_mode)");
                    e.printStackTrace();
                    // return Errors.ENOENT;
                    return -1;
                }
                fileinfo.raf = raf;
                if (!cache.add_reference_count(cache_path)) {
                    System.err.println("Reference Count Add Failed! " + cache_path);
                }
            }

            // Store access_mode for close() to decide whether forward update back to Server.
            fileinfo.access_mode = access_mode;
            return 0;

        }

        /**
        * @brief Get file from server
        * @param path remote file path on server
        * @return an array of file bytes
        */
        private byte[] fetch_file(String path) {
            System.err.println("Proxy fetch_file()");
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
            System.err.println("Proxy save_file_locally(), path: " + cache_path);

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

        /**
        * @brief Get file from server with chunking, and save it locally, 
        * @brief an integration of fetch_file and save_file_locally
        * @param path remote file path on server
        * @param offset file offset used for chunking
        * @return an array of file bytes
        */
        private byte[] fetch_save_huge_file(String path, String cache_path, long file_size) {
            System.err.println("Proxy fetch_save_huge_file() with chunking");
            byte[] received_file = null;
            long offset = 0;
            try {
                RandomAccessFile tmp = new RandomAccessFile(cache_path, "rw");
                while (offset < file_size) {
                    received_file = srv.get_file(path, offset);
                    offset += chunk_size;
                    tmp.write(received_file);
                }
                tmp.close();
            } catch (IOException e) {
                System.err.println("Proxy: fetch_save_huge_file() failed");
                e.printStackTrace();
            }
            return null;
        }


        /**
        * @brief create a file copy for writting on local cache directory
        * add it to cache_line, also sets the write_path in fileinfo
        * @param fileinfo fileinfo, to be paired with fd
        * @param fd fd paired with fileinfo, used for writing copy's name
        */
        private int create_write_copy(FileInfo fileinfo, int fd) {
            System.err.println("Proxy: create_write_copy()");
            String tmp = get_cache_path(fileinfo.orig_path);
            String write_path = tmp + "_wr_" + fd;

            String cache_path = fileinfo.path;

            // Only a to-write file has write_path stored in the fileinfo
            fileinfo.write_path = write_path;
            File file = new File(write_path);
            if (cache.get_cache_remain_size() < fileinfo.file_size) {
                // BUG HERE! if you evict for write copies, it may be possible to evict your own read copy
                // So you cannot do files.copy() here!
                boolean is_enough = cache.evict(fileinfo.file_size);
                if (!is_enough) {
                    // Errors.ENOMEM
                    return -2;
                }
            }
            // source: https://www.journaldev.com/861/java-copy-file
            try {
                Files.copy((new File(cache_path)).toPath(), (new File(write_path)).toPath());
                cache.add_to_cacheline_write_ver(new CachedFileInfo(fileinfo));
            } catch (IOException e) {
                System.err.println("Proxy: create_write_copy() failed copy file");
                e.printStackTrace();
            }
            // Problem: you can't simply add to cacheline here, because it adds the cache_path (_rdonly_)
            // But if you don't add, cache size does not change and you cannot track it?
            // Is it really necessary to add this temporary file into the cache_line and path_file_map?
            return 0;
        }

    }
    
    private static class FileHandlingFactory implements FileHandlingMaking {
        public FileHandling newclient() {
            return new FileHandler();
        }
    }

    private static boolean check_path(String cache_path) {
        return cache_path.contains(cache_dir);
    }

    /**
    * @brief Get local cache path by adding cache directory
    * @param path original input file path
    * @return concatenated local cache path
    */
    private static String get_cache_path(String path) {
        StringBuilder sb = new StringBuilder(cache_dir);
        sb.append("/");
        path = path.replace("/", ";;");
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
    * @brief Set file information in the map
    * @param ... A series of data defined in FileInfo.java
    * @return file information class used in fd_file map
    */
    private static FileInfo set_fileinfo_value(boolean is_existed, boolean is_dir, int remote_version_num, int remote_file_size, String cache_path, String path, RandomAccessFile raf) {
        FileInfo fileinfo = new FileInfo();
        fileinfo.is_existed = is_existed;
        fileinfo.is_dir = is_dir;
        fileinfo.version = remote_version_num;
        fileinfo.file_size = remote_file_size;
        fileinfo.path = cache_path;
        fileinfo.orig_path = path;
        fileinfo.raf = raf;
        return fileinfo;
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