import java.rmi.*;


public interface RemoteOps extends Remote {

    public Reply_FileInfo get_file_info(String path) throws RemoteException;

    public byte[] get_file(String path) throws RemoteException;

    // public int update_files() throws RemoteException;

    // public void send_files() throws RemoteException;


}