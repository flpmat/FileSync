package au.edu.unimelb.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import au.edu.unimelb.push.push.CheckFileStateThread;
import au.edu.unimelb.push.push.NextInstructionThread;
import au.edu.unimelb.thread.pull.GetInstructionsThread;
import filesync.SynchronisedFile;

public class Client {

	/**
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException,
			InterruptedException {
		// Arguments
		String filename = null, hostname = null, direction = "push";
		int serverport = 4144, blocksize = 1024;
		// Get arguments from command line.
		for (int flag = 0; flag < args.length; flag += 2) {
			if (args[flag].equals("-file")) {
				filename = args[flag + 1];
			}
			if (args[flag].equals("-host")) {
				hostname = args[flag + 1];
			}
			if (args[flag].equals("-p")) {
				serverport = Integer.parseInt(args[flag + 1]);
			}
			if (args[flag].equals("-b")) {
				blocksize = Integer.parseInt(args[flag + 1]);
			}
			if (args[flag].equals("-d")) {
				direction = args[flag + 1];
			}
		}
		// Creates a new SychronisedFile object.
		SynchronisedFile localFile = null;
		try {
			localFile = new SynchronisedFile(filename, blocksize);
		} catch (Exception e) {
			System.out.println("File Not Found");// TODO: handle exception
		}

		// Connection objects.
		InetAddress host = InetAddress.getByName(hostname);
		DatagramSocket socket = null;
		DatagramPacket packet = null;

		/*
		 * The first message sent is a Negotiation Message. It gives the
		 * direction of the connection and the block size.
		 */
		String negotiationMsg = "{\"type\":\"negotiation\",\"blocksize\":"
				+ blocksize + ",\"direction\":\"" + direction + "\"}";
		byte[] buf = negotiationMsg.getBytes();
		/*
		 * The negotiation message will be sent from any port of the Client to
		 * the specified port on the Server.
		 */
		socket = new DatagramSocket();
		packet = new DatagramPacket(buf, buf.length, host, serverport);

		try {
			// Negotiation is sent.
			socket.send(packet);
			// A reply is received.
			socket.receive(packet);
			// The socket is closed (can be reloaded further).
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		/*
		 * The socket is bound to the specified port in order to receive
		 * instruction messages (pull direction).
		 */
		if (direction.equals("pull")) {
			buf = new byte[blocksize * 2];
			packet = new DatagramPacket(buf, buf.length);
			socket = new DatagramSocket(serverport);
		}
		/*
		 * If the direction is "push", the client will send instructions. Case
		 * pull, will receive instructions.
		 */
		if (direction.equals("push")) {
			// This thread check the file state.
			Thread checkFileStateThread = new Thread(new CheckFileStateThread(
					localFile));
			checkFileStateThread.start();
			// This thread gets the instructions and sends them.
			Thread nextInstructionThread = new Thread(
					new NextInstructionThread(localFile, host, serverport));
			nextInstructionThread.start();
		} else if (direction.equals("pull")) {
			/*
			 * Before starting a thread to receive instructions, the Client
			 * exchange messages with the server. This must happen because the
			 * GetInstructionsThread should always run first then
			 * CheckFileThread and NextInstructionThread.
			 */
			socket.receive(packet);
			sendReply("OK", packet, socket);
			Thread getInstructionsThread = new Thread(
					new GetInstructionsThread(localFile, socket, packet,
							blocksize));
			getInstructionsThread.start();
			sendReply("OK", packet, socket);
		}

	}

	public static void sendReply(String msg, DatagramPacket req,
			DatagramSocket socket) throws IOException {
		byte[] rbuf = msg.getBytes();
		DatagramPacket reply = new DatagramPacket(rbuf, rbuf.length,
				req.getAddress(), req.getPort());
		socket.send(reply);
	}
}
