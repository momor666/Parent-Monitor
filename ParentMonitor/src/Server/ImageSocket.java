package Server;

import static Server.Network.IMAGE_BUFFER_SIZE;
import Util.StreamCloser;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.imageio.ImageIO;

public final class ImageSocket implements Closeable {
    
    private Socket socket;
    private DataInputStream recieveImage;
    
    public ImageSocket(String host, int port) {
        try {
            (socket = new Socket()).connect(new InetSocketAddress(host, port), 100);
        }
        catch (IOException ex) {
            StreamCloser.close(socket);
            socket = null;
            ex.printStackTrace();
            return; //there's no point in continuing initialization
        }
       
        try {
            recieveImage = new DataInputStream(new BufferedInputStream(socket.getInputStream(), IMAGE_BUFFER_SIZE));
        }
        catch (IOException ex) {
            StreamCloser.close(socket);
            socket = null;
            ex.printStackTrace();
        }
    }

    public boolean isActive() {
        Socket socketReference = socket;
        DataInputStream recieveImageReference = recieveImage;
        return (socketReference == null || recieveImageReference == null) ? false : !socketReference.isClosed();
    }

    @Override
    public void close() {
        //load instance variables first
        Socket socketReference = socket;
        DataInputStream recieveImageReference = recieveImage;
        //close local references at the same time
        StreamCloser.close(socketReference);
        StreamCloser.close(recieveImageReference);
        //dispose of instance variables
        socket = null;
        recieveImage = null;
    }

    public BufferedImage readImage() throws IOException {
        DataInputStream imageStream = recieveImage; //avoid getfield opcode
        byte[] imageBytes = new byte[imageStream.readInt()];
        imageStream.readFully(imageBytes);
        return ImageIO.read(new ByteArrayInputStream(imageBytes));
    }
    
    @Override
    public String toString() {
        return socket == null ? "Not Connected" : socket.toString();
    }
}