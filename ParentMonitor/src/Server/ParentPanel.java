package Server;

import static Server.Network.CLIENT_EXITED;
import static Server.Network.CLOSE_CLIENT;
import static Server.Network.PUNISH;
import static Server.ServerFrame.SCREEN_BOUNDS;
import Util.StreamCloser;
import Util.ThreadSafeBoolean;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

public final class ParentPanel extends JPanel implements Runnable {

    //thread control
    private final ThreadSafeBoolean terminated;

    //reference to ServerFrame's tabs, so we can remove ourselves from the
    //tabs when necessary
    private JTabbedPane parentTabs;
    private TextFrame parentConnectionHistory;
    //do not dispose this here, ServerFrame must take care of this to ensure proper closing
    //of the Server application, all threads and frames must be closed for our application to exit
    //without System.exit()

    //stream variable
    private TextSocket textConnection;

    private JSplitPane split;
    private ClientPanel client;
    private TextPanel text;

    //info variables
    private Map<String, String> clientEnvironment;
    private String clientName;
    private TextFrame clientInfoFrame; //Must be disposed!

    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    public ParentPanel(ServerFrame parent, TextSocket clientTextConnection, ImageSocket clientImageConnection) throws IOException {
        //MUST PERFORM INITIAL READ
        Map<String, String> clientData;
        try {
            //contains all client data
            //Device Name
            //Device OS
            //Device User Name
            //Device SystemEnv
            String[] data = clientTextConnection.readText().split(Pattern.quote("|"));
            int length = data.length;
            clientData = new LinkedHashMap<>(length);
            System.out.println("Reading System Data from: " + clientTextConnection.toString());
            String delimiter = Pattern.quote("->");
            for (int index = 0; index < length; ++index) {
                String[] entry = data[index].split(delimiter);
                System.out.println("Read: " + entry[0] + " -> " + entry[1]);
                clientData.put(entry[0], entry[1]);
            }
            clientEnvironment = clientData;
        }
        catch (IOException | ArrayIndexOutOfBoundsException ex) {
            //clean up used resources only
            StreamCloser.close(clientTextConnection);
            ex.printStackTrace();
            throw ex;
        }

        terminated = new ThreadSafeBoolean(false);

        parentTabs = parent.getTabs();
        parentConnectionHistory = parent.getConnectionHistoryFrame();

        //setup SplitPanel with: ClientPanel & TextPanel with cheeky initialization
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                client = new ClientPanel(parent,
                        clientName = clientData.containsKey("USERNAME")
                        ? clientData.get("USERNAME")
                        : "Unknown",
                        clientImageConnection),
                text = new TextPanel((textConnection = clientTextConnection).getOutputStream()));
        split.setLeftComponent(client);
        split.setRightComponent(text);
        split.setDividerLocation(SCREEN_BOUNDS.width / 2);

        //add components
        super.setLayout(new GridLayout(1, 1));
        super.add(split);

        //add other stuff
        super.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        super.setToolTipText("Client Username: " + clientName);

        clientInfoFrame = new TextFrame(parent, parent.getIconImage(), clientName + " System Information", getClientSystemInfo(), true);

        new Thread(this, clientName + " Main Client Manager Thread").start();
    }

    public void setSelected(boolean current) {
        client.setRepaint(current); //To improve performance, only update the live feed it its visible
    }

    public JSplitPane getSplitPane() {
        return split;
    }

    @Override
    public String getName() {
        return clientName;
    }

    public String getClientSystemInfo() {
        StringBuilder builder = new StringBuilder("Client System Information:\n");
        for (Iterator<Map.Entry<String, String>> it = clientEnvironment.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, String> entry = it.next();
            builder.append(entry.getKey()).append(" -> ").append(entry.getValue());
            if (it.hasNext()) {
                builder.append("\n");
            }
            else {
                break;
            }
        }
        return builder.toString();
    }

    @Override
    public Component[] getComponents() {
        return new Component[]{client, text};
    }

    public void saveCurrentShot(ImageBank bank, ScreenShotDisplayer master) {
        client.saveCurrentShot(bank, master);
    }

    /*
    public void toggleUpdate() {
        client.toggleUpdate();
    }
     */
    
    public boolean takenScreenShot() {
        return client.takenScreenShot();
    }

    public void showSavedScreenShots() {
        client.showScreenShotDisplayer();
    }

    public void showInfo() {
        clientInfoFrame.setVisible(true);
    }

    public synchronized void close(boolean serverClosedClient) {
        if (terminated.get()) { //Lock
            return;
        }

        parentTabs.remove(this);
        parentTabs = null;

        if (serverClosedClient) { //indicates wheather the server intentionally closed the client
            textConnection.sendText(CLOSE_CLIENT); //Inform client server has disconnected them
            parentConnectionHistory.addText(clientName + " disconnected by Server: " + new Date());
        }
        else {
            parentConnectionHistory.addText(clientName + " disconnected from Server: " + new Date());
        }

        //DO NOT DISPOSE HISTORY, IT IS A REFERENCE TO THE SERVER
        //SERVER WILL HANDLE IT
        parentConnectionHistory = null;

        StreamCloser.close(textConnection);
        textConnection = null;

        split.removeAll();
        split = null;

        //The Image Retriever Thread will close first, then the Manager Thread
        //will exit after this method has finished execution. Since the Render thread sleeps often
        //it is likely to be the last one to stop
        client.close();
        client = null;

        text.setEnabled(false);
        text.setVisible(false);
        text = null;

        clientEnvironment.clear();
        clientEnvironment = null;
        clientName = null;

        //Dispose all frames 
        clientInfoFrame.dispose();
        clientInfoFrame = null;

        terminated.set(true); //Unlock at the very end, to prevent many threads from missing things
    }

    public synchronized void punish() {
        if (terminated.get()) { //Lock
            return;
        }

        parentTabs.remove(this);
        parentTabs = null;

        textConnection.sendText(PUNISH); //Inform client server has PUNISHED them
        parentConnectionHistory.addText(clientName + " shutdown by Server: " + new Date());

        //DO NOT DISPOSE HISTORY, IT IS A REFERENCE TO THE SERVER
        //SERVER WILL HANDLE IT
        parentConnectionHistory = null;

        StreamCloser.close(textConnection);
        textConnection = null;

        split.removeAll();
        split = null;

        //The Image Retriever Thread will close first, then the Manager Thread
        //will exit after this method has finished execution. Since the Render thread sleeps often
        //it is likely to be the last one to stop
        client.close();
        client = null;

        text.setEnabled(false);
        text.setVisible(false);
        text = null;

        clientEnvironment.clear();
        clientEnvironment = null;
        clientName = null;

        //Dispose all frames 
        clientInfoFrame.dispose();
        clientInfoFrame = null;

        terminated.set(true); //Unlock at the very end, to prevent many threads from missing things
    }

    @Override
    public final void run() {
        TextSocket textStream = textConnection; //avoid getfield opcode
        while (!terminated.get()) {
            try {
                String fromClient = textStream.readText();
                if (CLIENT_EXITED.equals(fromClient)) {
                    close(false);
                    break;
                }
                else {
                    text.updateChatPanel(clientName, fromClient);
                }
            }
            catch (IOException ex) {
                close(false);
                System.err.println("Failed to recieve message from client.");
                ex.printStackTrace();
                //If the client has been forcibly terminated on their end
                //without sending the final exit message, such as from manual
                //shutdown, we must take care to destroy the client on this end as well
                break;
            }
        }
        System.out.println("Manager Exiting. Client Name Should Be Set to Null: " + clientName);
        //clientName should be set to null here, since close() has been called
    }

    /**
     * SOLVED ALREADY, was previously in Thread Loop. BIG BUG AHEAD!!! * BUG: If
     * the server requests an image from the client and suddenly stops
     * requesting, the last message will be sent as "Server Request: Screenshot"
     *
     * This causes the BufferedReader readLine() below to hold until the client
     * texts back in a conversation message or a closing notification. This
     * means that until the client communicates back, and the updateScreenShot
     * flag has been set to false, this method (and Thread) will be frozen!!!
     * Yet we still have to use readLine() for other client activities, so we
     * may have to resort to using a thread safe stack here...
     *
     * Fortunately there are no other significant problems save this one.
     *
     * SOLUTIONS: - A stupid workaround would be to instead send a message to
     * the client that the server is not requesting screenshots, and the client
     * would promptly respond back, preventing a holdup here.
     *
     * A side effect of this naive solution is that this application may take up
     * unnecessary network operation space
     *
     * For now we will use the naive solution, since I'd rather not make a
     * concurrent stack and keep accessing it repeatedly.
     *
     * UPDATE: The naive solution works, but is unstable if the host and the
     * client are constantly chatting, which clogs up the BufferedReader
     *
     * Therefore, we will implement a 2-Socket solution. This has been done.
     */
    //The following line of code is based off the naive solution
    //sendText.println(updateScreenShot.get() ? REQUEST_IMAGE : STOP_REQUEST_IMAGE);
}