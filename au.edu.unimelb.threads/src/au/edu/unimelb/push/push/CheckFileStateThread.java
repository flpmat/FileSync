package au.edu.unimelb.push.push;

import java.io.IOException;

import filesync.SynchronisedFile;

/*
 * This thread checks the file state. It repeats within a 5 seconds interval.
 */
public class CheckFileStateThread implements Runnable {
	private SynchronisedFile localFile = null;

	public CheckFileStateThread(SynchronisedFile localFile) {
		this.localFile = localFile;
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run() {
		while (true) {
			try {
				System.out.println("Checking File (Client)...");
				localFile.CheckFileState();
				Thread.sleep(5000);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// TODO Auto-generated method stub
	}

}
