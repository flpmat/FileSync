package au.edu.unimelb.push.push;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import filesync.CopyBlockInstruction;
import filesync.Instruction;
import filesync.NewBlockInstruction;
import filesync.SynchronisedFile;

public class NextInstructionThread implements Runnable {
	private SynchronisedFile localFile = null;
	private int port;
	private InetAddress host;
	private DatagramSocket socket = null;

	/*
	 * This constructor receives all the information required to open a
	 * connection.
	 */
	public NextInstructionThread(SynchronisedFile localFile, InetAddress host,
			int port) throws SocketException {
		this.localFile = localFile;
		this.host = host;
		this.port = port;
		socket = new DatagramSocket();
	}

	@Override
	public void run() {
		int instructionCounter = 1;
		String rep;
		String jst = null; // JSON String with the instruction
		Instruction instruction; // Instruction object used throughout the
									// code

		while ((instruction = localFile.NextInstruction()) != null) {
			jst = instruction.ToJSON();
			// Sends instruction
			System.out.println("Sending: " + jst);
			send(makeInstructionMsg(jst, instructionCounter));
			// Receive reply
			rep = getReply();
			/*
			 * The next block reads the feedback message to know if the
			 * instruction needs to be resent or not. While receiving
			 * "Expecting" messages, the last instruction will be resent. When
			 * an "Ack" message is received, the next instruction can be sent.
			 * An "Exception" message triggers the last instruction update,
			 * which will be sent again.
			 */
			try {
				JSONObject jsonObject = (JSONObject) new JSONParser()
						.parse(rep);
				if (jsonObject.get("type").equals("expecting")
						&& ((Number) jsonObject.get("counter")).intValue() == instructionCounter) {
					do {
						send(makeInstructionMsg(jst, instructionCounter));
						rep = getReply();
						jsonObject = (JSONObject) new JSONParser().parse(rep);
					} while (!jsonObject.get("type").equals("ack"));
					instructionCounter = ((Number) jsonObject.get("counter"))
							.intValue() + 1;
				} else if (jsonObject.get("type").equals("exception")
						&& ((Number) jsonObject.get("counter")).intValue() == instructionCounter) {
					do {
						send(updateInstruction(instruction, instructionCounter));
						rep = getReply();
						jsonObject = (JSONObject) new JSONParser().parse(rep);
					} while (!jsonObject.get("type").equals("ack"));
					instructionCounter = ((Number) jsonObject.get("counter"))
							.intValue() + 1;
					// criar funcao wait for ack
				} else if (jsonObject.get("type").equals("ack")
						&& ((Number) jsonObject.get("counter")).intValue() == instructionCounter) {
					instructionCounter = ((Number) jsonObject.get("counter"))
							.intValue() + 1;
				}
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/*
	 * Function to send messages.
	 */
	public void send(String jst) {
		byte[] buf = jst.getBytes();
		DatagramPacket block = new DatagramPacket(buf, buf.length, host, port);
		try {
			socket.send(block);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * Function to receive replies.
	 */
	public String getReply() {
		byte[] rbuf = new byte[2048];
		DatagramPacket reply = new DatagramPacket(rbuf, rbuf.length);
		try {
			socket.receive(reply);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new String(reply.getData(), reply.getOffset(), reply.getLength());
	}

	/*
	 * Function to make a complete message.
	 */
	public static String makeInstructionMsg(String jst, int instructionCounter) {
		String msg = "{\"type\":\"inst\",\"inst\":" + jst + ",\"counter\":"
				+ (instructionCounter) + "}";
		return msg;
	}

	/*
	 * Function to update a copyblock instruction into a newblock instruction.
	 */
	public static String updateInstruction(Instruction instruction,
			int instructionCounter) {
		Instruction upgraded = new NewBlockInstruction(
				(CopyBlockInstruction) instruction);
		String completeJstUpgraded = "{\"type\":\"inst\",\"inst\":"
				+ upgraded.ToJSON() + ",\"counter\":" + (instructionCounter)
				+ "}";
		System.out.println("upgraded" + completeJstUpgraded);
		return completeJstUpgraded;
	}

}
