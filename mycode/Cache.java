import java.util.*;

public class Cache {

    private List<FileInfo> cache_line;
    private Iterator<FileInfo> itr;
    private Map<String, FileInfo> path_file_map;

    public Cache(String cache_dir, int cache_size) {
        cache_line = new LinkedList<FileInfo>();
        itr = cache_line.iterator();
        path_file_map = new HashMap<String, FileInfo>();
    }

    /**
     * @brief get version id
     * @param[in] path String path to check
     * @return 0 if not in cache, otherwise version num
     */
    public int get_local_version(String path) {
        if (path_file_map.containsKey(path)) {
            return (path_file_map.get(path)).version;
        }
        // while (itr.hasNext()) {
        //     FileInfo current_fileinfo = itr.next();
        //     if (current_fileinfo.path.equals(path)) {
        //         return current_fileinfo.version;
        //     }
        // }
        return 0;
    }

    /**
     * @brief add file to cache mapping and cache line
     * @param fileinfo file information to add
     */
    public void add_file(FileInfo fileinfo) {
        cache_line.add(fileinfo);
        path_file_map.put(fileinfo.path, fileinfo);
    }

    /**
     * @brief get file info from cache mapping
     * @param path file path to get from
     * @return locally stored fileinfo in cache
     */
    public FileInfo get_local_file_info(String path) {
        if (path_file_map.containsKey(path)) {
            return path_file_map.get(path);
        } else {
            return null;
        }
    }

    public boolean create_cache(String cache_dir, int cache_size) {
        return true;
    }

}