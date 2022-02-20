import java.io.*;
import java.io.Serializable;

public class Reply_FileInfo implements Serializable{
    public boolean is_dir;
    public boolean is_existed;
    public int version;
    public long file_size;
    public Reply_FileInfo() {

    }
}