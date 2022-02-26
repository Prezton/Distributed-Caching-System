import java.io.*;


/**
* @brief file information used on proxy
*/
public class FileInfo{
    public RandomAccessFile raf;
    public boolean is_dir;
    public boolean is_existed;
    public int version;
    public int file_size;
    public String path;
    public String orig_path;
    public String access_mode;
    public String read_path;
    public String write_path;
    public FileInfo() {

    }
}