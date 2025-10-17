package utb.fai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private static final long serialVersionUID = 1L;
    private ConcurrentHashMap<String, SocketHandler> activeHandlersSet = new ConcurrentHashMap<String, SocketHandler>();

    /**
     * sendMessageToAll - Pole zprávu vem aktivním klientùm kromì sebe sama
     * 
     * @param sender  - reference odesílatele
     * @param message - øetìzec se zprávou
     */
    synchronized void sendMessageToAll(SocketHandler sender, String message) {
        for (SocketHandler handler : activeHandlersSet.values()) {
            if (handler != sender && !Collections.disjoint(sender.groups, handler.groups)) {
                if (!handler.messages.offer("[" + sender.userName +"] >> " + message)) {
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.userName);
                }
            }
        }
    }

    void setName(String name, SocketHandler handler) {
        String oldKey = null;

        for (Map.Entry<String, SocketHandler> entry : activeHandlersSet.entrySet()) {
            if (entry.getValue() == handler) {
                oldKey = entry.getKey();
                break;
            }
        }

        if (oldKey != null) {
            activeHandlersSet.remove(oldKey);
        }

        activeHandlersSet.put(name, handler);
    }

    boolean existsName(String name) {
        return activeHandlersSet.containsKey(name);
    }

    SocketHandler getByName(String name) {
        return activeHandlersSet.get(name);
    }

    /**
     * add pøidá do mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má pøidat.
     * @return true if the set did not already contain the specified element.
     */
    synchronized void add(SocketHandler handler) {
        activeHandlersSet.put(handler.clientID, handler);
    }

    /**
     * remove odebere z mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má odstranit
     * @return true if the set did not already contain the specified element.
     */
    synchronized void remove(SocketHandler handler) {
        activeHandlersSet.values().remove(handler);
    }
}
