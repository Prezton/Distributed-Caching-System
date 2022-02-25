import java.rmi.*;


public interface RemoteOps extends Remote {

    public Reply_FileInfo get_file_info(String path) throws RemoteException;

    public byte[] get_file(String path) throws RemoteException;

    public void create_file(String path) throws RemoteException;

    public int upload_file(String path, byte[] sent_file) throws RemoteException;

    // public void send_files() throws RemoteException;


}