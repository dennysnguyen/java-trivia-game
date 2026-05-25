import java.io.*;
import java.net.*;
import java.util.List;

public class ClientHandler implements Runnable {
    private Socket socket;
    private GameServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username = null;
    private int score = 0;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        try {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("ClientHandler init error: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                handleMessage(line.trim());
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + (username != null ? username : "unknown"));
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleMessage(String msg) {
        // Protocol messages from client:
        // REGISTER|username|password
        // LOGIN|username|password
        // ANSWER|answerIndex
        String[] parts = msg.split("\\|", -1);
        if (parts.length == 0) return;

        switch (parts[0]) {
            case "REGISTER":
                if (parts.length >= 3) {
                    String result = server.registerUser(parts[1], parts[2]);
                    send(result);
                }
                break;
            case "LOGIN":
                if (parts.length >= 3) {
                    String result = server.loginUser(parts[1], parts[2]);
                    if (result.equals("LOGIN_OK")) {
                        username = parts[1];
                        score = 0;
                    }
                    send(result);
                    // If game already in progress, send current question
                    if (result.equals("LOGIN_OK") && server.isGameStarted()) {
                        // Send current question to late joiner
                        int idx = server.getCurrentQuestionIdx();
                        if (idx < server.getTotalQuestions()) {
                            List<String> q = server.getQuestions().get(idx);
                            String qMsg = "QUESTION|" + idx + "|" + server.getTotalQuestions() + "|"
                                    + q.get(0) + "|" + q.get(1) + "|" + q.get(2) + "|" + q.get(3) + "|" + q.get(4);
                            send(qMsg);
                        }
                    }
                }
                break;
            case "ANSWER":
                if (parts.length >= 2 && username != null) {
                    try {
                        int answerIdx = Integer.parseInt(parts[1]);
                        // Run in background so it doesn't block this client's thread
                        new Thread(() -> server.receiveAnswer(username, answerIdx)).start();
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                break;
            default:
                System.out.println("Unknown message from client: " + msg);
        }
    }

    public void send(String message) {
        if (out != null) out.println(message);
    }

    public String getUsername() { return username; }
    public int getScore() { return score; }
    public void incrementScore() { score++; }
    public void resetScore() { score = 0; }
}
