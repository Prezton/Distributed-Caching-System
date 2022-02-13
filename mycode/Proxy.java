/* Sample skeleton for proxy */

import java.io.*;
import java.util.*;

class Proxy {

    /**
     * map between fd and RandomAccessFile Object
     */
	private static Map<Integer, FileInfo> fd_file_map = new HashMap<Integer, FileInfo>();
	private static int fd_count = 0;

	private static class FileInfo {
		public RandomAccessFile raf;
		public boolean is_dir;
		public FileInfo() {

		}
	}
	
	private static class FileHandler implements FileHandling {

		public synchronized int open( String path, OpenOption o ) {
			// Invalid path argument
			if (path == null || path.equals("")) {
				return Errors.EINVAL;
			}
			System.err.print("open, mode: ");
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
			File file = new File(path);
			if (check_existed) {
				boolean whether_existed = file.exists();
				if (whether_existed) {
					return Errors.EEXIST;
				}
			}
			
			RandomAccessFile raf;
			FileInfo fileinfo = new FileInfo();
			fileinfo.is_dir = file.isDirectory();
			
			try {
				raf = new RandomAccessFile(file, access_mode);
				fileinfo.raf = raf;
			} catch (FileNotFoundException e) {
				System.err.println("exception in open: RandomAccessFile");
				e.printStackTrace();
				return Errors.ENOENT;
			}
			fd_count += 1;
			int intFD = fd_count + raf.hashCode();
			fd_file_map.put(intFD, fileinfo);

			return intFD;
		}

		public synchronized int close( int fd ) {
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}
			System.err.println("close");

			FileInfo fileinfo = fd_file_map.get(fd);
			RandomAccessFile raf = fileinfo.raf;
			if (raf.equals(null)) {
				System.out.println("this would not happen normally");
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
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}
			System.err.println("write");

			if (buf == null) {
				return Errors.EINVAL;
			}

			FileInfo fileinfo = fd_file_map.get(fd);
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
			// System.out.println("read");
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}
			System.err.println("read");

			if (buf == null) {
				return Errors.EINVAL;
			}
			FileInfo fileinfo = fd_file_map.get(fd);
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
			if (!fd_file_map.containsKey(fd)) {
				return Errors.EBADF;
			}
			if (pos < 0) {
				return Errors.EINVAL;
			}
			System.err.println("lseek");

			FileInfo fileinfo = fd_file_map.get(fd);
			RandomAccessFile raf = fileinfo.raf;

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
			if (path == null) {
				return Errors.EINVAL;
			}
			File file = new File(path);
			if (!file.exists()) {
				return Errors.ENOENT;
			}
			System.err.println("unlink");

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
		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}

