public class CachedFileInfo {
    public boolean is_dir;
    public boolean is_existed;
    public int version;
    public int file_size;
    public String path;
    public String orig_path;
    public String access_mode;
    public CachedFileInfo() {

    }

    public CachedFileInfo(FileInfo fileinfo) {
        this.is_dir = fileinfo.is_dir;
        this.is_existed = fileinfo.is_existed;
        this.version = fileinfo.version;
        this.file_size = fileinfo.file_size;
        this.path = fileinfo.path;
        this.orig_path = fileinfo.orig_path;
        this.access_mode = fileinfo.access_mode;
    }

}