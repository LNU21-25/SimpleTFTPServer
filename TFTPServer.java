//package assignment3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * A simple TFTP Server.
 */
public class TFTPServer {
  public static final int TFTPPORT = 4970;
  public static final int BUFSIZE = 516;
  public static final String READDIR = "./read/"; //custom address at your PC
  public static final String WRITEDIR = "./read/"; //custom address at your PC
  // OP codes
  public static final int OP_RRQ = 1;
  public static final int OP_WRQ = 2;
  public static final int OP_DAT = 3;
  public static final int OP_ACK = 4;
  public static final int OP_ERR = 5;

  final int TIMEOUT_DURATION = 1000;

  /**
   * The main method to start the TFTP Server.
   * It does not take any command line arguments.

   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    System.out.println("TFTP server started.");
    if (args.length > 0) {
      System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
      System.exit(1);
    } 
    // Attempt to start server.
    try {
      TFTPServer server = new TFTPServer();
      server.start();
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  /**
   * Start the TFTP Server.

   * @throws SocketException If the socket encounters an error.
   */
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

                String directory = (reqtype == OP_RRQ) ? READDIR : WRITEDIR;
                HandleRQ(sendSocket, directory + requestedFile, reqtype);
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
    File file = new File(requestedFile);
    if (opcode == OP_RRQ && !file.exists()) {
      send_ERR(sendSocket, 1, "File not found");
      return;
    }
    if (opcode == OP_RRQ) {
      boolean result = send_DATA_receive_ACK(sendSocket, requestedFile);
      System.out.println("Reading was " + (result ? "successful" : "unsuccessful"));
    } else if (opcode == OP_WRQ) {
      boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
      System.out.println("Writing was " + (result ? "successful" : "unsuccessful"));
    } else {
      send_ERR(sendSocket, 4, "Illegal TFTP operation");
    }
  }

  private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, String requestedFile) {
    try (FileInputStream fis = new FileInputStream(requestedFile)) {
      boolean lastPacket = false;
      int blockNumber = 1;
      while (!lastPacket) {
        byte[] readBytes = new byte[BUFSIZE - 4];
        int bytesRead = fis.read(readBytes);
        lastPacket = bytesRead < BUFSIZE - 4;

        ByteBuffer buf = ByteBuffer.allocate(BUFSIZE);
        buf.putShort((short) OP_DAT);
        buf.putShort((short) blockNumber);
        buf.put(readBytes, 0, bytesRead);

        DatagramPacket dataPacket = new DatagramPacket(buf.array(), bytesRead + 4, sendSocket.getInetAddress(), sendSocket.getPort());
        boolean ackReceived = sendPacketWithRetries(sendSocket, dataPacket, blockNumber);
        if (!ackReceived) {
          return false;
        }
        blockNumber++;
      }
      return true;
    } catch (IOException e) {
      send_ERR(sendSocket, 0, "Failed to read file: " + e.getMessage());
      return false;
    }
  }

  private boolean sendPacketWithRetries(DatagramSocket sendSocket, DatagramPacket packet, int blockNumber) {
    for (int attempt = 0; attempt < 5; attempt++) {
      try {
        sendSocket.send(packet);
        sendSocket.setSoTimeout(TIMEOUT_DURATION);
        DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4);
        sendSocket.receive(ackPacket);

        ByteBuffer ackBuf = ByteBuffer.wrap(ackPacket.getData());
        if (ackBuf.getShort() == OP_ACK && ackBuf.getShort() == blockNumber) {
          return true;
        }
      } catch (IOException e) {
        System.err.println("Attempt " + attempt + " failed for block " + blockNumber);
      }
    }
    send_ERR(sendSocket, 0, "Max retries reached for block " + blockNumber);
    return false;
  }

  private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, String requestedFile) {
    try (FileOutputStream fos = new FileOutputStream(requestedFile, false)) {
      send_ACK(sendSocket, 0);

      int blockNumber = 1;
      while (true) {
        byte[] receiveBuf = new byte[BUFSIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
        sendSocket.receive(receivePacket);

        ByteBuffer buf = ByteBuffer.wrap(receivePacket.getData());
        short opcode = buf.getShort();
        int receivedBlockNumber = Short.toUnsignedInt(buf.getShort());

        if (opcode != OP_DAT || receivedBlockNumber != blockNumber) {
          send_ERR(sendSocket, 4, "Unexpected packet or block number.");
          return false;
        }

        int dataLength = receivePacket.getLength() - 4;
        if (dataLength < 512) {
          fos.write(receiveBuf, 4, dataLength);
          send_ACK(sendSocket, blockNumber);
          break;
        } else {
          fos.write(receiveBuf, 4, dataLength);
          send_ACK(sendSocket, blockNumber++);
        }
      }
      return true;
    } catch (IOException e) {
      send_ERR(sendSocket, 0, "IO error: " + e.getMessage());
      return false;
    }
  }

  private void send_ACK(DatagramSocket sendSocket, int blockNumber) {
    ByteBuffer buf = ByteBuffer.allocate(4);
    buf.putShort((short) OP_ACK);
    buf.putShort((short) blockNumber);
    try {
      sendSocket.send(new DatagramPacket(buf.array(), buf.array().length, sendSocket.getInetAddress(), sendSocket.getPort()));
    } catch (IOException e) {
      System.err.println("Failed to send ACK for block " + blockNumber);
    }
  }

  private void send_ERR(DatagramSocket sendSocket, int errorCode, String errorMsg) {
    ByteBuffer errBuffer = ByteBuffer.allocate(errorMsg.length() + 5);
    errBuffer.putShort((short) OP_ERR);
    errBuffer.putShort((short) errorCode);
    errBuffer.put(errorMsg.getBytes());
    errBuffer.put((byte) 0);

    DatagramPacket errPacket = new DatagramPacket(errBuffer.array(), errBuffer.position(), sendSocket.getInetAddress(), sendSocket.getPort());
    try {
      sendSocket.send(errPacket);
    } catch (IOException e) {
      System.err.println("Error sending error packet: " + e.getMessage());
    }
  }
}
