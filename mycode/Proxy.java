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

			if (buf == null) {
				return Errors.EINVAL;
			}

			RandomAccessFile raf = fd_file_map.get(fd);

			try {
				raf.write(buf);
			} catch (IOException e) {
				return Errors.EBADF;
			}
			return buf.length;

		}

		public long read( int fd, byte[] buf ) {
			// System.out.println("read");
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}

			if (buf == null) {
				return Errors.EINVAL;
			}
			RandomAccessFile raf = fd_file_map.get(fd);

			try {
				long length = (long) raf.read(buf);
				if (length == -1) {
					return 0;
				}
				return length;
			} catch (IOException e) {
				return Errors.EBADF;
			}
			

		}

		public long lseek( int fd, long pos, LseekOption o ) {
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}
			if (pos < 0) {
				return Errors.EINVAL;
			}
			RandomAccessFile raf = fd_file_map.get(fd);
			long new_pos = pos;
			if (o == LseekOption.FROM_CURRENT) {
				try {
					long current_offset = raf.getFilePointer();
					new_pos = pos + current_offset;
					raf.seek(new_pos);
				} catch (IOException e) {
					// question about those IOExceptions!!!
					return Errors.EINVAL;
				}

			} else if (o == LseekOption.FROM_END) {
				try {
					long length = raf.length();
					new_pos = length - pos;
					raf.seek(new_pos);
				} catch (IOException e) {
					return Errors.EINVAL;
				}
			} else if (o == LseekOption.FROM_START) {
				try {
					new_pos = pos;
					raf.seek(new_pos);
				} catch (IOException e) {
					return Errors.EINVAL;
				}
			}
			return new_pos;
		}

		public int unlink( String path ) {
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
		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}

