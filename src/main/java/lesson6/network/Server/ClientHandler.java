package lesson6.network.Server;

import lesson6.database.JdbcInteractor;
import lesson6.network.ChatUtilizer;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static lesson6.utils.Share.*;

public class ClientHandler implements ChatUtilizer {
    private ChatServer server;
    private JdbcInteractor ji;
    private Socket socket;
    private String nickname;
    private String connectId;   // /127.0.0.1@@55191
    private String color;
    private TreeSet<String> blacklist;
    private long startTime;
    private HistoryFile hf = new HistoryFile();
    private ExecutorService executorServer;

    private DataInputStream is = null;
    private DataOutputStream os = null;

    public ClientHandler(ChatServer server, Socket socket, JdbcInteractor ji) {

        try {
            this.color = systemColor;
            this.server = server;
            this.socket = socket;
            this.ji = ji;
            this.is = new DataInputStream(socket.getInputStream());
            this.os = new DataOutputStream(socket.getOutputStream());
            this.startTime = System.currentTimeMillis();
            this.connectId = getConnectId();

            executorServer = Executors.newSingleThreadExecutor();
            executorServer.execute(() -> {
                try {

                    // Цикл аутентификации
                    if ((ClientHandler.this.nickname = authenticationLoop()) != null) {
                        ClientHandler.this.color = server.assignColor();
                        server.subscribe(ClientHandler.this);
                        blacklist = ji.getBlackList(nickname);
                        server.broadcastMessage(ClientHandler.this, addMetaData(" joined to chat"));
                        conversationLoop();
                    }
                } catch (IOException e) {
                    System.out.println("Client " + nickname + " disconnected");
                } finally {
                    server.unsubscribe(ClientHandler.this);
                    closeResources(is, os, socket);
                }
            });
            executorServer.shutdown();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Цикл аутентификации
    @Override
    public String authenticationLoop() throws IOException {
        String message = null;
        String nick = null;

        while ((message = is.readUTF()) != null) {
            if (message.matches(REGEX_AUTH) /* /auth login password */) {
                String[] parts = message.split("\\s", 3);
                nick = ji.getNickByLoginPass(parts[PROT_LOGIN], parts[PROT_PASSWORD]);
                if (nick != null) {
                    if (!server.isNickBusy(nick)) {
                        // Сообщить об успешной аутентификации и отправить nickname
                        sendMessage(PROT_MSG_AUTH_OK + SEPARATOR + nick);

                        StringBuilder sb = null;
                        try {
                            sb = hf.getHistoryChat();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (sb != null) {
                            String[] mass = sb.toString().split("\n");
                            for (int i = 0; i < mass.length; i++) {
                                String[] partsMass = mass[i].split(" ", 2);
                                sendMessage(partsMass[0] + SEPARATOR + "grey" + SEPARATOR + partsMass[1]);
                            }
                        }

                        break;
                    } else {
                        sendMessage(PROT_MSG_AUTH_NICK_BUSSY);
                    }
                } else {
                    sendMessage(PROT_MSG_AUTH_ERROR);
                }
            }
        }
        return nick;
    }

    // Цикл обработки сообщений
    @Override
    public void conversationLoop() throws IOException {
        String message = null;

        while ((message = is.readUTF()) != null) {
            if (message.startsWith(PROT_CMD_PREFIX)) {
                commandProcessor(message);
            } else {
                server.broadcastMessage(this, addMetaData(message));
                hf.saveHistory(nickname, message);
                System.out.print("[" + currentTime() + ": " + nickname + "]: " + message + System.lineSeparator());
            }
        }
    }

    // Обработка комманд
    @Override
    public void commandProcessor(String command) throws IOException {
        if (command.startsWith(PROT_MSG_TO) /* /w@@nick@@message */) {
            String[] parts = command.split(SEPARATOR, 3);
            server.sendTo(this, parts[1], addMetaData(parts[2]));
            sendMessage(addMetaData(parts[2]));
        } else if (command.equals(PROT_MSG_END /* /end */)) {
            server.broadcastMessage(this, addMetaData(" has left the chat"));
            /**
             * Нужно добавить код отключения коннекта
             */
        } else if (command.startsWith(PROT_MSG_BLOCK)) {  /* "/block nickBl1 nickBl2 ..." */
            List<String> ll = new LinkedList<>(Arrays.asList(command.split("\\s")));
            ll.remove(0);                           /* remove "/block" */
            // Нельзя добавлять свой ник в черный список
            if (ll.contains(nickname)) ((LinkedList<String>) ll).remove(nickname);
            // Обновить кеш blacklist
            for (String s : ll) {
                blacklist.add(s);
            }
            // Обновить sqlite blacklist
            ji.addToBlackList(nickname, new TreeSet<>(ll));
        } else if (command.startsWith(PROT_MSG_SHOW_BL)) {   /* /showbl */
            ji.getBlackList(nickname).forEach((s) -> System.out.println(s));
        } else if (command.startsWith(PROT_MSG_UNBLOCK)) {
            List<String> ll = new LinkedList<>(Arrays.asList(command.split("\\s")));
            ll.remove(0);                           /* remove "/block" */
            // Обновить кеш blacklist
            for (String s : ll) {
                blacklist.remove(s);
            }
            // Обновить sqlite blacklist
            ji.removeFromBlackList(nickname, new TreeSet<>(ll));
        }
    }

    // Закрыть сокет при простое
    public boolean inIdleState() {
        boolean b = false;
        if (nickname == null) {
            if (msecToSec(System.currentTimeMillis() - startTime) > IDLE_TIMEOUT) {
                sendMessage(PROT_MSG_IDLE + SEPARATOR + "Server Connection Timeout");
                closeResources(is, os, socket);
                b = true;
            }
        }
        return b;
    }

    // Get nickname
    public String getNickname() {
        return nickname;
    }

    // Добавить служебный заголовок к сообщению
    public String addMetaData(String message) {
        return nickname + SEPARATOR + color + SEPARATOR + message;
    }

    // Отправить сообщение в сокет
    public void sendMessage(String message) {
        try {
            os.writeUTF(message);
            os.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Получить blacklist
    public TreeSet<String> getBlacklist() {
        return blacklist;
    }

    // Уникальный ID
    public String getConnectId() {
        return socket.getInetAddress() + SEPARATOR + socket.getPort();
    }
}
