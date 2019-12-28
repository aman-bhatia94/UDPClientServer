import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.HashMap;

public class Client {

    InetAddress address;
    int serverPort;
    int maxPacketsReceived; //will be set
    int maxPackets;
    HashMap<Integer,DatagramPacket> savedPackets;

    Client(InetAddress address, int serverPort){
        this.address = address;
        this.serverPort = serverPort;
        savedPackets = new HashMap<>();
        maxPacketsReceived = 0;
        maxPackets = 0;
    }

    public void startClient(){
        try(DatagramSocket socket = new DatagramSocket()){
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while(true) {
                socket.setSoTimeout(0);
                System.out.println("Enter your request to send");
                String standardInput = reader.readLine();
                if(standardInput == null){
                    socket.close();
                    break;
                }
                byte[] sendBuffer = standardInput.getBytes();
                DatagramPacket packet = new DatagramPacket(sendBuffer,sendBuffer.length,address,serverPort);
                socket.send(packet);
                String message = new String(packet.getData());
                //System.out.println("Data sent to server: "+message);

                String dataFromServer;
                //int maxPacket = 0;
                while(true){
                    byte[] readBuffer = new byte[1024];
                    DatagramPacket packet1 = new DatagramPacket(readBuffer,readBuffer.length);
                    socket.setSoTimeout(2000); //timeout of 10 seconds set, if nothing received, move on
                    try {
                        socket.receive(packet1);
                        maxPacketsReceived++;
                        dataFromServer = new String(packet1.getData()).trim();
                        String splitter = extractHeader(dataFromServer);
                        String checkError = extractActualData(dataFromServer);
                        //System.out.println("Data received from the server: "+checkError);
                        String[] splitHeader = splitHeader(splitter);
                        int seqNo = Integer.parseInt(splitHeader[0]);
                        maxPackets = Integer.parseInt(splitHeader[1]);
                        if (checkError.equals("400")) {
                            System.out.println("Bad request made, make a new request");
                            maxPacketsReceived = 0;
                            maxPackets = 0;
                            break;
                        } else if (checkError.equals("401")) {
                            System.out.println("File not specified in the request, make a new request");
                            maxPacketsReceived = 0;
                            maxPackets = 0;
                            break;
                        } else if (checkError.equals("404")) {
                            System.out.println("File not found on the server, make a new request");
                            maxPacketsReceived = 0;
                            maxPackets = 0;
                            break;
                        } else {
                            //Here we add our received packets in a data structure
                            //After the timeout expires, we will
                            //iterate this data structure to check, if there is
                            //a missing packet, if it is, then we request again
                            String ms = new String(packet1.getData());
                            savedPackets.put(seqNo,packet1);
                        }
                        if(maxPacketsReceived == maxPackets){
                            //All packets received, no need to check for missing packets
                            displayDataFromServer(savedPackets);
                            byte[] sendAckBuffer = "received".getBytes();
                            DatagramPacket packetAck = new DatagramPacket(sendAckBuffer,sendAckBuffer.length,address,serverPort);
                            socket.send(packetAck);
                            savedPackets.clear();
                            maxPacketsReceived = 0;
                            maxPackets = 0;
                            break;
                        }
                    } catch (Exception e) {
                        //Here all the packets that were to arrive would have arrived
                        //Now we check for missing packets
                        //All our received packets have been saved in a hashmap
                        //we also know the number of maximum packets
                        //we iterate till the maximum packets
                        //if one is missing we request it
                        checkMissingPackets(socket);
                        break;
                    }
                }


            }
        } catch (SocketException e) {
            System.out.println("Socket could not be opened\n"+e);
        } catch (IOException e) {
            System.out.println("Unable to read from the input"+e);
        }
    }

    private void checkMissingPackets(DatagramSocket socket) throws IOException {
        byte[] readBuffer = new byte[1024];
        System.out.println("maxPackets: "+ maxPackets);
        System.out.println("maxPacketsReceived: "+ maxPacketsReceived);
        if(maxPackets != maxPacketsReceived) {

            //ask for missing packets, only if there are missing packets
            for (int i = 1; i <= maxPackets; i++) {

                if(!savedPackets.containsKey(i)){
                    //Request for packet i
                    String resendString = "resendpckt_no"+String.valueOf(i);
                    byte[] reSendBuffer = resendString.getBytes();
                    DatagramPacket reSendPacket = new DatagramPacket(reSendBuffer,reSendBuffer.length,address,serverPort);
                    socket.send(reSendPacket);

                    try{
                        DatagramPacket packetReSent = new DatagramPacket(readBuffer,readBuffer.length);
                        socket.setSoTimeout(2000);
                        socket.receive(packetReSent);
                        String reSenDataFromServer = new String(packetReSent.getData()).trim();
                        String splitter = extractHeader(reSenDataFromServer);
                        String[] splitHeader = splitHeader(splitter);
                        int seqNo = Integer.parseInt(splitHeader[0]);
                        savedPackets.put(seqNo,packetReSent);
                    }
                    catch (Exception ex){
                        System.out.println("Unable to receive missing packet, exiting...");
                        savedPackets.clear();
                        socket.close();
                    }
                }
                //Printing to the client the file data

            }
        }
        displayDataFromServer(savedPackets);
        byte[] sendAckBuffer = "received".getBytes();
        DatagramPacket packet = new DatagramPacket(sendAckBuffer,sendAckBuffer.length,address,serverPort);
        socket.send(packet);
        savedPackets.clear();
        maxPacketsReceived = 0;
        maxPackets = 0;
    }

    private void displayDataFromServer(HashMap<Integer, DatagramPacket> savedPackets) {

        for(int i = 1; i <= savedPackets.size(); i++){

            try {
               String packetData = new String(savedPackets.get(i).getData()).trim(); //check trim
                String data = extractActualData(packetData);
                if(data.equals("___linebr___")){
                    System.out.println();
                }
                else{
                    System.out.println(data);
                }
            }
            catch (NullPointerException e){

                //do nothing, continue
                System.out.println("Error while reading data, check stack strace\n"+e);

            }


        }
    }

    private String extractHeader(String dataFromServer) {

        String[] splitter = dataFromServer.split("h_end");
        return splitter[0];
    }

    private String extractActualData(String dataFromServer){
        String[] splitter = dataFromServer.split("h_end");
        return splitter[1];
    }

    private String[] splitHeader(String header){
        String[] headerSplit = header.split("mx_end");
        return headerSplit;
    }

    public static void main(String[] args) {
        try {
            Client client = new Client(InetAddress.getByName("localhost"),4444);
            client.startClient();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

}
