package assignment3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

/**
 * A simple TFTP Server.
 */
public class TFTPServer {

  public static final int TFTPPORT = 69;
  public static final int BUFSIZE = 516;
  public static final String READDIR = "./read/"; //custom address at your PC
  public static final String WRITEDIR = "./write/"; //custom address at your PC
  // OP codes
  public static final int OP_RRQ = 1;
  public static final int OP_WRQ = 2;
  public static final int OP_DAT = 3;
  public static final int OP_ACK = 4;
  public static final int OP_ERR = 5;

  /**
   * The main method.

   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    if (args.length > 0) {
      System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
      System.exit(1);
    }
    // Starting the server.
    try {
      TFTPServer server = new TFTPServer();
      server.start();
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  private void start() throws SocketException {
    byte[] buf = new byte[BUFSIZE];

    // Create socket
    DatagramSocket socket = new DatagramSocket(null);

    // Create local bind point 
    SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
    socket.bind(localBindPoint);

    System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

    // Loop to handle client requests 
    while (true) {        
      final InetSocketAddress clientAddress = receiveFrom(socket, buf);

      if (clientAddress == null) {
        continue;
      }
      final StringBuffer requestedFile = new StringBuffer();
      final int reqtype = ParseRQ(buf, requestedFile);

      new Thread() {
        public void run() {
          try {
            DatagramSocket sendSocket = new DatagramSocket(0);
            // Connect to client
            sendSocket.connect(clientAddress);

            System.out.printf("%s request from %s using port %d\n",
                (reqtype == OP_RRQ) ? "Read" : "Write",
                clientAddress.getHostName(), clientAddress.getPort());

            // Read request
            if (reqtype == OP_RRQ) {
              requestedFile.insert(0, READDIR);
              System.out.println("Read dir: " + requestedFile.toString());
              HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
            } else {
                requestedFile.insert(0, WRITEDIR);
                System.out.println("Write dir: " + requestedFile.toString());
                HandleRQ(sendSocket, requestedFile.toString(), OP_WRQ);
            }
            sendSocket.close();
          } catch (SocketException e) {
            e.printStackTrace();
          }
        }
      }.start();
    }
  }

  /**
  * Reads the first block of data, i.e., the request for an action (read or write).

  * @param socket (socket to read from)
  * @param buf (where to store the read data)
  * @return socketAddress (the socket address of the client)
  */
  private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
    DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
    try {
      socket.receive(receivePacket);
      InetSocketAddress socketAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
      return socketAddress;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Parses the request in buf to retrieve the type of request and requestedFile.

   * @param buf (received request)
   * @param requestedFile (name of file to read/write)
   * @return opcode (request type: RRQ or WRQ)
   */
  private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
    // See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents

    ByteBuffer wrap = ByteBuffer.wrap(buf);
    short opcode = wrap.getShort();
    if (opcode == OP_RRQ) {
      // Read Request
      int index = 2;
      while (buf[index] != 0) {
        requestedFile.append((char) buf[index]);
        index++;
      }
    } else if (opcode == OP_WRQ) {
      // Write Request
      int index = 2;
      while (buf[index] != 0) {
        requestedFile.append((char) buf[index]);
        index++;
      }
    } else {
      System.err.println("Invalid request opcode: " + opcode);
      return -1;
    }
    return opcode;
  }

  /**
  * Handles RRQ and WRQ requests.

  * @param sendSocket (socket used to send/receive packets)
  * @param requestedFile (name of file to read/write)
  * @param opcode (RRQ or WRQ)
  */
  private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {
    if (opcode == OP_RRQ) {
      // See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
      //boolean result = send_DATA_receive_ACK(sendSocket, requestedFile);
      File requestedFileObj = new File(requestedFile);
      if (!requestedFileObj.exists()) {
        System.err.println("File not found: " + requestedFile);
        send_ERR(sendSocket, 1, "File not found");
        return;
      }
      boolean result = send_DATA_receive_ACK(sendSocket, requestedFile);
      if (!result) {
        System.err.println("Error reading file: " + requestedFile);
        send_ERR(sendSocket, 1, "File not found");
      } else {
        System.out.println("File sent successfully.");
      }
    } else if (opcode == OP_WRQ) {
      //boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
      boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
      if (!result) {
        System.err.println("Error writing file: " + requestedFile);
        send_ERR(sendSocket, 4, "Error writing file");
      } else {
        System.out.println("File received successfully.");
      }
    } else {
      System.err.println("Invalid op code. Sending an error packet.");
      send_ERR(sendSocket, 5, "Invalid request");
      return;
    }
  }

  private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, String requestedFile) {
    try (FileInputStream fis = new FileInputStream(requestedFile)) {
      int blockNumber = 1;
      byte[] buffer = new byte[BUFSIZE - 4]; // 4 bytes for opcode and block number
      int bytesRead;
  
      while ((bytesRead = fis.read(buffer)) != -1) {
        ByteBuffer packetBuffer = ByteBuffer.allocate(bytesRead + 4);
        packetBuffer.putShort((short) OP_DAT); // Opcode for Data packet
        packetBuffer.putShort((short) blockNumber); // Block number
        packetBuffer.put(buffer, 0, bytesRead);
  
        DatagramPacket dataPacket = new DatagramPacket(packetBuffer.array(), packetBuffer.position(), sendSocket.getInetAddress(), sendSocket.getPort());
  
        int retries = 0;
        byte[] ackBuf = new byte[4];
        while (retries < 5) {
          sendSocket.send(dataPacket);

          DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
          try {
            sendSocket.setSoTimeout(5000);
            sendSocket.receive(ackPacket);
            break;
          } catch (SocketTimeoutException e) {
            System.err.println("Timeout waiting for ACK for block " + blockNumber);
            retries++;
          } catch (IOException e) {
            System.err.println("Unexpected IO error: " + e.getMessage());
            send_ERR(sendSocket, 1, "Error reading file");
            return false;
          }
        }
  
        if (retries == 5) {
          send_ERR(sendSocket, 4, "File not found");
          return false;
        }

        ByteBuffer ackBuffer = ByteBuffer.wrap(ackBuf);
        int ackOpcode = ackBuffer.getShort();
        int ackBlockNumber = ackBuffer.getShort();
  
        if (ackOpcode != OP_ACK || ackBlockNumber != blockNumber) {
          send_ERR(sendSocket, 0, "Invalid ACK packet received");
          continue;
        }
  
        blockNumber++;
      }
  
      if (bytesRead < BUFSIZE - 4) {
        ByteBuffer ackBuffer = ByteBuffer.allocate(4);
        ackBuffer.putShort((short) OP_ACK);
        ackBuffer.putShort((short) blockNumber);
  
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer.array(), ackBuffer.position(), sendSocket.getInetAddress(), sendSocket.getPort());
        sendSocket.send(ackPacket);
      }
  
      // All data sent successfully
      fis.close();
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, String requestedFile) {
    try (FileOutputStream fos = new FileOutputStream(requestedFile)) {
      int expectedBlockNumber = 1;

      while (true) {
        ByteBuffer ackBuffer = ByteBuffer.allocate(4); // ACK packet size
        ackBuffer.putShort((short) OP_ACK); // Opcode for ACK packet
        ackBuffer.putShort((short) expectedBlockNumber); // Expected block number

        DatagramPacket ackPacket = new DatagramPacket(ackBuffer.array(), ackBuffer.position(), sendSocket.getInetAddress(), sendSocket.getPort());
        sendSocket.send(ackPacket);

        // Receive Data packet
        byte[] buf = new byte[BUFSIZE];
        DatagramPacket dataPacket = new DatagramPacket(buf, buf.length);
        sendSocket.receive(dataPacket);

        ByteBuffer dataBuffer = ByteBuffer.wrap(buf);
        int opcode = dataBuffer.getShort();
        int blockNumber = dataBuffer.getShort();

        if (opcode != OP_DAT || blockNumber != expectedBlockNumber) {
          // Invalid packet received
          System.err.println("Invalid data packet received. Excepted block number: " + expectedBlockNumber + ", received: " + blockNumber);
          send_ERR(sendSocket, 0, "Invalid data packet received");
          return false;
        }

        // Write data to file
        fos.write(buf, 4, dataPacket.getLength() - 4);

        // Send ACK for the received data packet
        sendSocket.send(ackPacket);

        if (dataPacket.getLength() < BUFSIZE - 4) {
          break; // Last data packet received
        }

        expectedBlockNumber++;
      }
      // All data received successfully
      fos.close();
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("ioexception");
      return false;
    }
  }

  private void send_ERR(DatagramSocket sendSocket, int errorCode, String errorMsg) {
    ByteBuffer errBuffer = ByteBuffer.allocate(errorMsg.length() + 5); // 5 bytes for opcode and error code
    errBuffer.putShort((short) OP_ERR); // Opcode for Error packet
    errBuffer.putShort((short) errorCode); // Error code
    errBuffer.put(errorMsg.getBytes());
    errBuffer.put((byte) 0); // Null terminator

    DatagramPacket errPacket = new DatagramPacket(errBuffer.array(), errBuffer.position(), sendSocket.getInetAddress(), sendSocket.getPort());
    try {
      sendSocket.send(errPacket);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}


