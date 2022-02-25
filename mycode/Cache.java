import java.util.*;
import java.io.*;
import java.util.concurrent.*;

public class Cache {

    private LinkedList<CachedFileInfo> cache_line;
    private Map<String, CachedFileInfo> path_file_map;
    private int current_cache_size;
    private final int total_cache_size;
    private String cache_dir;

    public Cache(String cache_dir, int cache_size) {
        cache_line = new LinkedList<CachedFileInfo>();
        path_file_map = new ConcurrentHashMap<String, CachedFileInfo>();
        total_cache_size = cache_size;
        current_cache_size = 0;
        this.cache_dir = cache_dir;
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
        System.err.println("Proxy: add_to_cacheline()");
        String cache_path = cached_fileinfo.path;
        if (!path_file_map.containsKey(cache_path)) {
            // Not in cache yet
            cache_line.add(cached_fileinfo);
            path_file_map.put(cached_fileinfo.path, cached_fileinfo);
            current_cache_size += cached_fileinfo.file_size;
        }
    }

    public void update_version(String cache_path) {
        (path_file_map.get(cache_path)).version += 1;
    }

    /**
     * @brief remove existing file in LinkedList and HashMap, and delete it.
     * @param cache_path file path to be removed
     */
    public synchronized boolean remove_file(String cache_path, int new_version) {
        assert(path_file_map.containsKey(cache_path));
        CachedFileInfo cached_fileinfo = path_file_map.get(cache_path);
        assert(new_version != cached_fileinfo.version);
        boolean is_removed = cache_line.remove(cached_fileinfo);
        CachedFileInfo tmp = path_file_map.remove(cache_path);
        File file = new File(cache_path);
        file.delete();
        if (!is_removed || (tmp == null)) {
            System.err.println("Proxy remove_file: failed to remove CachedFileInfo from list or map" + is_removed + " " + tmp);
            return false;
        }
        current_cache_size -= cached_fileinfo.file_size;
        return true;
    }

    /**
    * @brief Delete stale files in the cache
    * @param cache_path paths such as foo_rdonly_version id
    * @return concatenated local cache path
    */
    public void delete_old_versions(String orig_path, int new_version) {
        System.err.println("Proxy: delete_old_versions");
        String partial_path = get_cache_path(orig_path) + "_rdonly_";

        // Delete all cached files that are older than this version, and have a reference count of 0
        // Iterator<CachedFileInfo> itr = cache_line.iterator();
        
        for (int i = 0; i < new_version; i ++) {
            String tmp_path = partial_path + i;
            File tmp_file = new File(tmp_path);
            if (tmp_file.exists()) {

                if (path_file_map.containsKey(tmp_path) && path_file_map.get(tmp_path).reference_count <= 0) {
                    System.err.println("old versions DELETED!");

                    tmp_file.delete();
                    CachedFileInfo to_delete = path_file_map.remove(tmp_path);
                    cache_line.remove(to_delete);
                    current_cache_size -= to_delete.file_size;
                } else {
                    System.err.println("delete_old_versions(): file exists but not in path_file_map");
                }
            }
        }

        
    }


    /**
     * @brief remove existing file in LinkedList and HashMap, and delete it.
     * @param cached_fileinfo cached_fileinfo to be removed
     */
    // private synchronized void remove_file_v2(CachedFileInfo cached_fileinfo) {
    //     String cache_path = cached_fileinfo.path;
    //     boolean is_removed = cache_line.remove(cached_fileinfo);
    //     CachedFileInfo tmp = path_file_map.remove(cache_path);
    //     File file = new File(cache_path);
    //     file.delete();
    //     if (!is_removed || (tmp == null)) {
    //         System.err.println("Proxy remove_file_v2: failed to remove CachedFileInfo from list or map" + is_removed + " " + tmp);
    //         return;
    //     }
    //     current_cache_size -= cached_fileinfo.file_size;

    // }

    /**
     * @brief update existing fileinfo in LinkedList and HashMap
     * @param cached_fileinfo file information to update
     */
    // private void update_file(CachedFileInfo cached_fileinfo) {
    //     String cache_path = cached_fileinfo.path;
    //     CachedFileInfo old_cached_fileinfo = path_file_map.get(cache_path);
    //     assert(old_cached_fileinfo.version != cached_fileinfo.version);

    //     remove_file_v2(cached_fileinfo);
    //     cache_line.add(cached_fileinfo);
    //     path_file_map.put(cache_path, cached_fileinfo);

    // }

    public void move_to_end(CachedFileInfo cached_fileinfo) {
        cache_line.remove(cached_fileinfo);
        cache_line.add(cached_fileinfo);
    }

    public synchronized boolean evict(long file_size) {
        System.err.println("Evict!");
        Iterator<CachedFileInfo> itr = cache_line.iterator();
        while (itr.hasNext()) {
            CachedFileInfo current = itr.next();
            boolean is_removed = cache_line.remove(current);
            if (!is_removed) {
                System.err.println("Cache: evict(), failed to evict the current element in cache_line list");
            }
            CachedFileInfo tmp2 = path_file_map.remove(current.path);
            if (tmp2 == null) {
                System.err.println("Cache: evict(), failed to evict the first element in path_file_map");
            }
            File file = new File(current.path);
            file.delete();
            current_cache_size -= current.file_size;
            if (get_cache_remain_size() >= file_size) {
                break;
            }
        }
        return (get_cache_remain_size() >= file_size);
    }

    public long get_cache_remain_size() {
        return (total_cache_size - current_cache_size);
    }

    public void traverse_cache() {
        System.err.println("Start traversal");
        Iterator<CachedFileInfo> itr = cache_line.iterator();
        while (itr.hasNext()) {
            CachedFileInfo current = itr.next();
            System.err.println("Node: " + current.path);
        }
    }

    public boolean add_reference_count(String cache_path) {
        if (path_file_map.containsKey(cache_path)) {
            path_file_map.get(cache_path).reference_count += 1;
        } else {
            return false;
        }
        return true;
    }

    public boolean decrease_reference_count(String cache_path) {
        if (path_file_map.containsKey(cache_path)) {
            path_file_map.get(cache_path).reference_count -= 1;
        } else {
            return false;
        }
        return true;
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



    /**
    * @brief Get local cache path by adding cache directory
    * @param path original input file path
    * @return concatenated local cache path
    */
    private String get_cache_path(String path) {
        StringBuilder sb = new StringBuilder(cache_dir);
        sb.append("/");
        sb.append(new StringBuilder(path));
        String cano_path = null;
        try {
            cano_path = (new File(sb.toString())).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cano_path;
    }

}