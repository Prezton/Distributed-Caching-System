/* Sample skeleton for proxy */

import java.io.*;
import java.util.Map;

class Proxy {

    /**
     * map between fd and RandomAccessFile Object
     */
	// private Map<> fd_file_map = new HashMap<Integer, File>();
	
	private static class FileHandler implements FileHandling {

		public synchronized int open( String path, OpenOption o ) {
			// Invalid path argument
			if (path == null) {
				return Errors.EINVAL;
			}
			String access_mode;
			if (o == CREATE) {
				access_mode = "rw"
			} else if (o == CREATE_NEW) {
				access_mode = "rw"
			} else if (o == READ) {
				access_mode = "r";
			} else if (o == WRITE) {
				access_mode = "rw";
			}



			RandomAccessFile raf = new RandomAccessFile(new File(path), o);
			return Errors.ENOSYS;
		}

		public int close( int fd ) {
			return Errors.ENOSYS;
		}

		public long write( int fd, byte[] buf ) {
			return Errors.ENOSYS;
		}

		public long read( int fd, byte[] buf ) {
			System.out.println("read");

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
		System.out.println("Hello World");
		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}

