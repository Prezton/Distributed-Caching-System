import java.io.*;

public class FileInfo implements Serializable{
    public RandomAccessFile raf;
    public boolean is_dir;
    public boolean is_existed;
    public int version;
    public FileInfo() {

    }
}