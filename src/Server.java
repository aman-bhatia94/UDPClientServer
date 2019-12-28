import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;

public class Server {

    private final static int port = 4444;
    private String directory;
    //private final String stale = "stale";
    //private final String fileNotFoundError = ""
    //refer the following error codes
    //file not found  404
    //Bad request by the client 400
    //No file specified 401


    ArrayList<DatagramPacket> savedPackets = new ArrayList<>();
    String errorHeader = "1mx_end1h_end";
    Server(String directory){
        this.directory = directory;
    }

    public void startServer(){

        try(DatagramSocket socket = new DatagramSocket(port)){
            System.out.println("Server started....");
            while(true){
                byte[] readBuffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(readBuffer, readBuffer.length);
                try {
                    socket.receive(packet);
                    //System.out.println("Client connected");
                    InetAddress address = packet.getAddress();
                    int clientPort = packet.getPort();
                    //additional checks maybe needed here
                    String inputData = new String(packet.getData()).trim();
                    //if client has sent received, clear the saved packets(which will also rest maxPackets
                    if(inputData.equals("received")){
                        savedPackets.clear();
                        continue;
                    }
                    else if(inputData.startsWith("resendpckt_no")){
                        //resend those packets
                        //receive for some time and then exit, if no response from server

                        String[] splitter = inputData.split("pckt_no");
                        String pcktNumber = splitter[1];
                        int packetToSend = Integer.parseInt(pcktNumber) - 1;
                        socket.send(savedPackets.get(packetToSend));
                        continue;
                    }
                    if(inputData.length() < 3){
                        System.out.println("Bad request by the client, client should make the request again");
                        String toSend = errorHeader+"400";
                        byte[] sendDataBuffer = toSend.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendDataBuffer,sendDataBuffer.length,address,clientPort);
                        socket.send(sendPacket);
                        continue;
                    }
                    else if(inputData.equals("index")){
                        System.out.println("Client request: "+inputData);
                        File folder = new File(directory);
                        File[] files = folder.listFiles();
                        StringBuilder listOfFiles = new StringBuilder();
                        for(File file: files){
                            listOfFiles.append(file.getName()+" ");
                        }
                        String toSend = listOfFiles.toString().trim();
                        byte[] sendDataBuffer = toSend.getBytes();//might need to change to UTF-8 for platform independence
                        DatagramPacket sendPacket = new DatagramPacket(sendDataBuffer,sendDataBuffer.length,address,clientPort);
                        String msg = new String(sendPacket.getData());
                        //System.out.println("Data to send to the client: "+msg);
                        savedPackets.add(sendPacket);
                        //call appendHeader method which appends headers to packets
                        appendHeaders();

                        socket.send(sendPacket);
                    }
                    else if(inputData.substring(0,3).equals("get")){
                        if(inputData.length() < 5){
                            System.out.println("No file specified, request again");
                            String toSend = errorHeader+"401";
                            byte[] sendDataBuffer = toSend.getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(sendDataBuffer,sendDataBuffer.length,address,clientPort);
                            socket.send(sendPacket);
                            continue;
                        }
                        String fileName = inputData.substring(4);
                        System.out.println("client requested get "+fileName);
                        fileName = fileName.trim();
                        //check for this file, return its contents, if available, then return contents of the file
                        File file = new File(directory+"/"+fileName);
                        if(!file.exists() || !fileName.endsWith(".txt")){
                            System.out.println("File does not exist or file is not a text file");
                            String toSend = errorHeader+"404";
                            byte[] sendDataBuffer = toSend.getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(sendDataBuffer,sendDataBuffer.length,address,clientPort);
                            socket.send(sendPacket);
                            continue;
                        }
                        if(file.exists()){
                            BufferedReader in = new BufferedReader(new FileReader(file));
                            String toSend;
                            byte[] okString = "ok".getBytes();
                            DatagramPacket sendOKPacket = new DatagramPacket(okString,okString.length,address,clientPort);
                            //socket.send(sendOKPacket);
                            //will add all these packets into the arraylist, append headers and keep sending them to the client
                            savedPackets.add(sendOKPacket);
                            while((toSend = in.readLine()) != null){
                                if(toSend.isBlank()){
                                    toSend = "___linebr___"; //special marker to identify blank lines
                                }

                                byte[] sendDataBuffer = toSend.getBytes();
                                DatagramPacket sendPacket = new DatagramPacket(sendDataBuffer,sendDataBuffer.length,address,clientPort);
                                //socket.send(sendPacket);
                                savedPackets.add(sendPacket);
                            }
                            //append file headers
                            appendHeaders();
                            sendFilePackets(socket);

                        }
                    }
                    else{
                        System.out.println("Bad request, request again..");
                        String toSend = errorHeader+"400";
                        byte[] sendDataBuffer = toSend.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendDataBuffer,sendDataBuffer.length,address,clientPort);
                        socket.send(sendPacket);
                        continue;
                    }
                } catch (IOException e) {
                    System.out.println("I/O exception occurred while sending or receiving data, check stack trace\n"+e);
                    socket.close();
                    break;
                }
            }

        } catch (SocketException e) {
            System.out.println("Socket could not bind to the port specified\n"+e);
        }

    }

    private void appendHeaders(){
        //will append headers to the packets in ArrayList
        int maxPackets = savedPackets.size();
        String dataFromPacket;
        for(int i = 0; i < maxPackets; i++){
            dataFromPacket = new String(savedPackets.get(i).getData());
            dataFromPacket = ""+String.valueOf(i+1)+"mx_end"+String.valueOf(maxPackets)+"h_end"+dataFromPacket;
            savedPackets.get(i).setData(dataFromPacket.getBytes());
        }
    }

    private void sendFilePackets(DatagramSocket socket) {
        for(DatagramPacket packet : savedPackets){
            String data = new String(packet.getData());
            try {

                socket.send(packet);
            }
            catch (Exception e){
                System.out.println("What is the error here: "+ e);
            }
        }
    }

    public static void main(String[] args) {
        String directory = args[0];
        Server server = new Server(directory);
        server.startServer();
    }

}
