import java.util.*;
import java.io.*;
import java.util.concurrent.*;

public class Cache {

    private List<CachedFileInfo> cache_line;
    private Iterator<CachedFileInfo> itr;
    private Map<String, CachedFileInfo> path_file_map;

    public Cache(String cache_dir, int cache_size) {
        cache_line = new LinkedList<CachedFileInfo>();
        itr = cache_line.iterator();
        path_file_map = new ConcurrentHashMap<String, CachedFileInfo>();
    }

    public boolean contains_file(String cache_path) {
        return path_file_map.containsKey(cache_path);
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
     * @brief add fileinfo to cache mapping and cache line
     * @param cached_fileinfo file information to add
     */
    public synchronized void add_to_cacheline(CachedFileInfo cached_fileinfo) {
        String cache_path = cached_fileinfo.path;
        if (!path_file_map.containsKey(cache_path)) {
            // Not in cache yet
            cache_line.add(cached_fileinfo);
            path_file_map.put(cached_fileinfo.path, cached_fileinfo);
        } else {
            // Already in cache, remote old one and add new one
            System.err.println("test2!!!");
            update_file(cached_fileinfo);
        }
    }

    public void update_file_version(String cache_path) {
        (path_file_map.get(cache_path)).version += 1;
    }

    /**
     * @brief remove existing file in LinkedList and HashMap
     * @param cache_path file path to be removed
     */
    public synchronized void remove_file(String cache_path) {
        while (itr.hasNext()) {
            CachedFileInfo current_fileinfo = itr.next();
            if (current_fileinfo.path.equals(cache_path)) {
                cache_line.remove(cache_path);
                File file = new File(cache_path);
                file.delete();
            }
        }
    }

    /**
     * @brief update existing fileinfo in LinkedList and HashMap
     * @param cached_fileinfo file information to update
     */
    private void update_file(CachedFileInfo cached_fileinfo) {
        String cache_path = cached_fileinfo.path;
        CachedFileInfo old_cached_fileinfo = path_file_map.get(cache_path);
        assert(old_cached_fileinfo.version != cached_fileinfo.version);

        // Overwrite old path-fileinfo map
        path_file_map.put(cache_path, cached_fileinfo);
        remove_file(cache_path);
        cache_line.add(cached_fileinfo);

    }

    /**
     * @brief get file info from cache mapping
     * @param path file path to get from
     * @return locally stored fileinfo in cache
     */
    public CachedFileInfo get_local_file_info(String path) {
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