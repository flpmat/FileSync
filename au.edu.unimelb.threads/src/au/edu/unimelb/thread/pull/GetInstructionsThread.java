package au.edu.unimelb.thread.pull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import filesync.BlockUnavailableException;
import filesync.Instruction;
import filesync.InstructionFactory;
import filesync.SynchronisedFile;

public class GetInstructionsThread implements Runnable {
	private SynchronisedFile localFile = null;

	private JSONObject jsonObject = null;

	private DatagramSocket socket = null;
	private DatagramPacket packet = null;

	private InstructionFactory instFactory = new InstructionFactory();
	private Instruction instruction;

	private int blocksize;
	private int expectingCounter = 1;

	private String instructionMessage = null;

	public GetInstructionsThread(SynchronisedFile localFile,
			DatagramSocket socket, DatagramPacket packet, int blocksize) {
		this.localFile = localFile;
		this.blocksize = blocksize;
		this.socket = socket;
		this.packet = packet;
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run() {
		while (true) {
			try {
				socket.receive(packet);
				instructionMessage = new String(packet.getData(),
						packet.getOffset(), packet.getLength());
				jsonObject = (JSONObject) new JSONParser()
						.parse(instructionMessage);
			} catch (IOException e3) {
				e3.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			/*
			 * Reads the type and the counter of the message. If it is an
			 * instruction and the counter matches with the expected, the
			 * instruction will be processed and the expected counter will be
			 * incremented for the next instruction. If the message counter is
			 * not the expected, the server will reply asking for resenting.
			 */
			if (jsonObject.get("type").equals("inst")
					&& ((Number) jsonObject.get("counter")).intValue() == expectingCounter) {
				/*
				 * The value in the "inst" field of the message is the actual
				 * instruction.
				 */
				instruction = instFactory.FromJSON(jsonObject.get("inst")
						.toString());
				/*
				 * The server tries to process the instruction and, in case of
				 * failing, a new instruction is requested. This behavior reflects
				 * the message types. When a file is changed a "NewBlock"
				 * message must be sent by the client (if a CopyBlock is
				 * received, the server will throw an exception).
				 */
				try {
					localFile.ProcessInstruction(instruction);
					System.out.println("Processing Instruction: "
							+ instructionMessage);
					sendReply(ack(jsonObject), packet, socket);
					expectingCounter++;
				} catch (BlockUnavailableException e) {
					try {
						sendReply(exception(jsonObject), packet, socket);
						Instruction upgradedInstruction = receiveNewBlock(
								blocksize, instFactory, jsonObject, socket);
						localFile.ProcessInstruction(upgradedInstruction);
						sendReply(ack(jsonObject), packet, socket);
						expectingCounter++;
					} catch (IOException e1) {
						e1.printStackTrace();
						System.exit(-1);
					} catch (BlockUnavailableException e2) {
						assert (false); // a NewBlockInstruction can never throw
										// this exception
					} catch (ParseException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					sendReply(expecting(jsonObject), packet, socket);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * Creates an acknowledgement message.
	 */
	public static String ack(JSONObject jsonObject) {
		String msg = "{\"type\":\"ack\",\"counter\":"
				+ (jsonObject.get("counter")) + "}";
		return msg;
	}

	/*
	 * Creates an exception message.
	 */
	public static String exception(JSONObject jsonObject) {
		String msg = "{\"type\":\"exception\",\"counter\":"
				+ (jsonObject.get("counter")) + "}";
		return msg;
	}

	/*
	 * Creates an expecting message.
	 */
	public static String expecting(JSONObject jsonObject) {
		String msg = "{\"type\":\"expecting\",\"counter\":"
				+ (jsonObject.get("counter")) + "}";
		return msg;
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

	/*
	 * This functions is used to request a NewBlock instruction.
	 */
	public static Instruction receiveNewBlock(int blocksize,
			InstructionFactory instFactory, JSONObject jsonObject,
			DatagramSocket socket) throws IOException, ParseException {
		byte[] bufException = new byte[blocksize * 2];
		DatagramPacket reqException = new DatagramPacket(bufException,
				bufException.length);

		socket.receive(reqException);

		String upgradedMessage = new String(reqException.getData(),
				reqException.getOffset(), reqException.getLength());
		jsonObject = (JSONObject) new JSONParser().parse(upgradedMessage
				.toString());
		upgradedMessage = jsonObject.get("inst").toString();

		Instruction upgradedInstruction = instFactory.FromJSON(upgradedMessage);
		System.out.println("Process Instruction: " + upgradedMessage);
		return upgradedInstruction;
	}

}
