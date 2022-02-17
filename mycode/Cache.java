import java.util.*;
import java.io.*;

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
     * @return -1 if not in cache, otherwise version num
     */
    public int get_local_version(String path) {
        if (path_file_map.containsKey(path)) {
            return (path_file_map.get(path)).version;
        }

        return -1;
    }

    /**
     * @brief add file to cache mapping and cache line
     * @param fileinfo file information to add
     */
    public synchronized void add_file(FileInfo fileinfo) {
        String cache_path = fileinfo.path;
        if (!path_file_map.containsKey(cache_path)) {
            // Not in cache yet
            cache_line.add(fileinfo);
            path_file_map.put(fileinfo.path, fileinfo);
        } else {
            // Already in cache, remote old one and add new one
            update_file(fileinfo);
        }
    }

    /**
     * @brief remove existing file in LinkedList and HashMap
     * @param cache_path file path to be removed
     */
    public synchronized void remove_file(String cache_path) {
        while (itr.hasNext()) {
            FileInfo current_fileinfo = itr.next();
            if (current_fileinfo.path.equals(cache_path)) {
                cache_line.remove(cache_path);
                File file = new File(cache_path);
                file.delete();
            }
        }
    }

    /**
     * @brief update existing fileinfo in LinkedList and HashMap
     * @param fileinfo file information to update
     */
    private void update_file(FileInfo fileinfo) {
        String cache_path = fileinfo.path;
        FileInfo old_fileinfo = path_file_map.get(cache_path);
        assert(old_fileinfo.version != fileinfo.version);

        // Overwrite old path-fileinfo map
        path_file_map.put(cache_path, fileinfo);
        remove_file(cache_path);

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