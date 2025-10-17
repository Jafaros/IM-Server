package utb.fai;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;

public class SocketHandler {
	/** mySocket je socket, o který se bude tento SocketHandler starat */
	Socket mySocket;

	/** client ID je øetìzec ve formátu <IP_adresa>:<port> */
	String clientID;

	// uzivatelske jmeno
	String userName = null;

	Set<String> groups = ConcurrentHashMap.newKeySet();

	/**
	 * activeHandlers je reference na mnoinu vech právì bìících SocketHandlerù.
	 * Potøebujeme si ji udrovat, abychom mohli zprávu od tohoto klienta
	 * poslat vem ostatním!
	 */
	ActiveHandlers activeHandlers;

	/**
	 * messages je fronta pøíchozích zpráv, kterou musí mít kaý klient svoji
	 * vlastní - pokud bude je pøetíená nebo nefunkèní klientova sí,
	 * èekají zprávy na doruèení právì ve frontì messages
	 */
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);

	/**
	 * startSignal je synchronizaèní závora, která zaøizuje, aby oba tasky
	 * OutputHandler.run() a InputHandler.run() zaèaly ve stejný okamik.
	 */
	CountDownLatch startSignal = new CountDownLatch(2);

	/** outputHandler.run() se bude starat o OutputStream mého socketu */
	OutputHandler outputHandler = new OutputHandler();
	/** inputHandler.run() se bude starat o InputStream mého socketu */
	InputHandler inputHandler = new InputHandler();
	/**
	 * protoe v outputHandleru nedovedu detekovat uzavøení socketu, pomùe mi
	 * inputFinished
	 */
	volatile boolean inputFinished = false;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;

		groups.add("public");
	}

	class OutputHandler implements Runnable {
		public void run() {
			OutputStreamWriter writer;
			try {
				System.err.println("DBG>Output handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Output handler running for " + clientID);
				writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
				while (!inputFinished) {
					String m = messages.take();// blokující ètení - pokud není ve frontì zpráv nic, uspi se!
					writer.write(m + "\r\n"); // pokud nìjaké zprávy od ostatních máme,
					writer.flush(); // poleme je naemu klientovi
					System.err.println("DBG>Message sent to " + (userName != null ? userName : clientID) + ":" + m + "\n");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.err.println("DBG>Output handler for " + clientID + " has finished.");

		}
	}

	class InputHandler implements Runnable {
		public void run() {
			try {
				System.err.println("DBG>Input handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Input handler running for " + clientID);
				String request = "";
				/**
				 * v okamiku, kdy nás Thread pool spustí, pøidáme se do mnoiny
				 * vech aktivních handlerù, aby chodily zprávy od ostatních i nám
				 */
				activeHandlers.add(SocketHandler.this);
				BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
				while ((request = reader.readLine()) != null) { // pøila od mého klienta nìjaká zpráva?
					// Kontrola úvodní zprávy
					if (userName == null) {
						if (request.contains(" ")) continue;
						userName = request;
						activeHandlers.setName(userName, SocketHandler.this);
						continue;
					}

					// Kontrola zda se jedna o prikaz
					if (request.startsWith("#")) {
						String[] parts = request.split(" ", 3);
    					String command = parts[0];

						switch (command) {
							case "#setMyName":
								if (parts.length < 2) {
									break;
								}

								String newName = parts[1];

								if (newName.contains(" ")) {
									break;
								}

								if (activeHandlers.existsName(newName)) {
									break;
								}

								userName = newName;

								activeHandlers.setName(newName, SocketHandler.this);
								break;

							case "#sendPrivate":
								if (parts.length < 3) {
									continue;
								}

								String targetName = parts[1];
								String privateMessage = parts[2];
								SocketHandler target = activeHandlers.getByName(targetName);

								if (target != null) {
									target.messages.offer("[" + userName + "] >> " + privateMessage);
								}

								break;
							case "#join":
								if (parts.length < 2) continue;
								groups.add(parts[1]);
								break;
							case "#leave":
								if (parts.length < 2) continue;
								groups.remove(parts[1]);
								break;
							case "#groups":
								messages.offer("Groups: " + String.join(",", groups));
            					break;
							default:
								messages.offer("Unknown command: " + command);
            					break;
						}
						
						continue;
					}

					System.out.println(request);
					activeHandlers.sendMessageToAll(SocketHandler.this, request);
				}
				inputFinished = true;
				messages.offer("OutputHandler, wakeup and die!");
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				// remove yourself from the set of activeHandlers
				synchronized (activeHandlers) {
					activeHandlers.remove(SocketHandler.this);
				}
			}
			System.err.println("DBG>Input handler for " + clientID + " has finished.");
		}

	}
}
