/* Sample skeleton for proxy */

import java.io.*;
import java.util.*;

class Proxy {

    /**
     * map between fd and RandomAccessFile Object
     */
	private static Map<Integer, RandomAccessFile> fd_file_map = new HashMap<Integer, RandomAccessFile>();
	
	private static class FileHandler implements FileHandling {

		public synchronized int open( String path, OpenOption o ) {
			// Invalid path argument
			if (path == null || path.equals("")) {
				return Errors.EINVAL;
			}

			String access_mode;
			boolean check_existed = false;
			if (o == OpenOption.CREATE) {
				access_mode = "rw";
			} else if (o == OpenOption.CREATE_NEW) {
				access_mode = "rw";
				check_existed = true;
			} else if (o == OpenOption.READ) {
				access_mode = "r";
			} else if (o == OpenOption.WRITE) {
				access_mode = "rw";
			} else {
				return Errors.EINVAL;
			}
			
			File file = new File(path);
			if (check_existed) {
				boolean whether_existed = file.exists();
				if (whether_existed) {
					return Errors.EEXIST;
				}
			}
			RandomAccessFile raf;
			try {
				raf = new RandomAccessFile(file, access_mode);
			} catch (FileNotFoundException e) {
				return Errors.ENOENT;
			}
			int intFD = raf.hashCode();
			fd_file_map.put(intFD, raf);

			return intFD;
		}

		public synchronized int close( int fd ) {
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}

			RandomAccessFile raf = fd_file_map.get(fd);
			if (raf.equals(null)) {
				System.out.println("this would not happen normally");
			}
			try {
				raf.close();
				fd_file_map.remove(fd);
			} catch (IOException e) {
				return -1;
			}
			return 0;
		}

		public synchronized long write( int fd, byte[] buf ) {
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}

			RandomAccessFile raf = fd_file_map.get(fd);
			try {
				raf.write(buf);
			} catch (IOException e) {
				return Errors.
			}
			return 0;

		}

		public long read( int fd, byte[] buf ) {
			// System.out.println("read");
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}
			return Errors.ENOSYS;
		}

		public long lseek( int fd, long pos, LseekOption o ) {
			return Errors.ENOSYS;
		}

		public int unlink( String path ) {
			return Errors.ENOSYS;
		}

		public void clientdone() {
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
		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}

