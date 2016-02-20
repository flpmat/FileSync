package au.edu.unimelb.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import au.edu.unimelb.push.push.CheckFileStateThread;
import au.edu.unimelb.push.push.NextInstructionThread;
import au.edu.unimelb.thread.pull.GetInstructionsThread;
import filesync.BlockUnavailableException;
import filesync.SynchronisedFile;

public class Server {

	/**
	 * @param args
	 * @throws BlockUnavailableException
	 * @throws IOException
	 * @throws ParseException
	 */
	public static void main(String[] args) throws BlockUnavailableException,
			IOException, ParseException {

		// Arguments
		int blocksize = 1024;
		int serverport = 4144;
		String filename = null;
		String direction = "push";
		// Get arguments from command line.
		for (int flag = 0; flag < args.length; flag += 2) {
			if (args[flag].equals("-file")) {
				filename = args[flag + 1];
			}
			if (args[flag].equals("-p")) {
				serverport = Integer.parseInt(args[flag + 1]);
			}
		}
		System.out.println("Server is Running");
		// File
		SynchronisedFile localFile = null;

		// Connection objects.
		byte[] buf = new byte[2048];
		DatagramSocket socket = new DatagramSocket(serverport);
		DatagramPacket packet = new DatagramPacket(buf, buf.length);

		// Variable and object used on reading and processing instructions.
		String instructionMessage;
		JSONObject jsonObject = null;

		// The first message is a negotiation instruction.
		socket.receive(packet);
		instructionMessage = new String(packet.getData(), packet.getOffset(),
				packet.getLength());
		jsonObject = (JSONObject) new JSONParser().parse(instructionMessage);
		System.out.println(instructionMessage);

		// Using the received package, the host can be retrieved.
		InetAddress host = packet.getAddress();

		/*
		 * Through a negotiation message, the Block size is given to the server
		 * in order to construct a SynchronisedFile object. Each message
		 * received is parsed to a JSONObject so its field can be read.
		 * Negotiation messages are always sent by the client when it starts.
		 */
		if (jsonObject.get("type").equals("negotiation")) {
			blocksize = ((Number) jsonObject.get("blocksize")).intValue();
			direction = jsonObject.get("direction").toString();
			try {
				localFile = new SynchronisedFile(filename, blocksize);
			} catch (Exception e) {
				System.out.println("File Not Found");// TODO: handle exception
			}
			sendReply("OK", packet, socket);
			/*
			 * A new buffer is set based on the block size given by the
			 * negotiation instruction. The buffer size must be bigger than the
			 * block size. A block is attached to a message so the received
			 * packet is always bigger than the block itself.
			 */
			buf = new byte[blocksize * 2];
			packet = new DatagramPacket(buf, buf.length);
			System.out.println("Negotiation Received");
			/*
			 * After a negotiation message is processed, the next instructions
			 * will be received. This will happen while the server is executing.
			 * The expecting counter is set always after a negotiation message
			 * (when the new instructions begin to be sent).
			 */
		}
		/*
		 * After receiving and processing a negotiation message, the socket is
		 * closed (it can be reloaded further).
		 */
		socket.close();

		if (direction.equals("pull")) {
			/*
			 * Before starting a thread to check the files, the Server exchange
			 * messages with the Client. This must happen because the
			 * GetInstructionsThread (executed in the Client when the direction
			 * is pull) should always run first than CheckFileThread and
			 * NextInstructionThread.
			 */
			socket = new DatagramSocket();
			packet = new DatagramPacket(buf, buf.length, host, serverport);
			sendReply("OK", packet, socket);
			socket.receive(packet);

			// This thread check the file state.
			Thread checkFileStateThread = new Thread(new CheckFileStateThread(
					localFile));
			checkFileStateThread.start();
			// This thread gets the instructions and sends them.
			Thread nextInstructionThread = new Thread(
					new NextInstructionThread(localFile, host, serverport));
			nextInstructionThread.start();
			socket.close();
		} else if (direction.equals("push")) {
			socket = new DatagramSocket(serverport);
			Thread getInstructionsThread = new Thread(
					new GetInstructionsThread(localFile, socket, packet,
							blocksize));
			getInstructionsThread.start();
		}
	}

	/*
	 * This function sends reply messages.
	 */
	public static void sendReply(String msg, DatagramPacket req,
			DatagramSocket socket) throws IOException {
		byte[] rbuf = msg.getBytes();
		DatagramPacket reply = new DatagramPacket(rbuf, rbuf.length,
				req.getAddress(), req.getPort());
		socket.send(reply);
	}
}
