/* Sample skeleton for proxy */

import java.io.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.util.*;

class Proxy {

    /**
     * map between fd and RandomAccessFile Object
     */
    private static Map<Integer, FileInfo> fd_file_map = new HashMap<Integer, FileInfo>();
    private static int fd_count = 0;
    private static final Object lock = new Object();
    private static final String server_name = "peizhaolServer";

    private static class FileInfo {
        public RandomAccessFile raf;
        public boolean is_dir;
        public FileInfo() {

        }
    }
    
    private static class FileHandler implements FileHandling {

        public synchronized int open( String path, OpenOption o ) {
            System.err.print( path + " open, mode: ");
            // Invalid path argument
            if (path == null || path.equals("")) {
                return Errors.EINVAL;
            }
            String access_mode;
            boolean check_existed = false;
            File file = new File(path);
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

            boolean is_dir = file.isDirectory();
            if (file.exists()) {
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
            }
            
            RandomAccessFile raf;
            FileInfo fileinfo = new FileInfo();
            fileinfo.is_dir = is_dir;
            System.err.println("File Type: is dir: " + is_dir + "exists: " + file.exists());
            try {
                if (!fileinfo.is_dir) {
                    raf = new RandomAccessFile(file, access_mode);
                    fileinfo.raf = raf;
                } else {
                    fileinfo.raf = null;
                }
            } catch (FileNotFoundException e) {
                System.err.println("exception in open: RandomAccessFile");
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
                System.err.println("exception in raf.write");
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

    }
    
    private static class FileHandlingFactory implements FileHandlingMaking {
        public FileHandling newclient() {
            return new FileHandler();
        }
    }

    public static void main(String[] args) throws IOException {
        // System.out.println("Hello World");

        String serverip = args[0];
        int port = Integer.parseInt(args[1]);
        String cache_dir = args[2];
        int cache_size = Integer.parseInt(args[3]);

        RemoteOps srv;

        try {
            // Look up reference in registry
            srv = (RemoteOps) Naming.lookup(server_name);
        } catch (NotBoundException e) {
            e.printStackTrace();
        }

        (new RPCreceiver(new FileHandlingFactory())).run();
    }
}

