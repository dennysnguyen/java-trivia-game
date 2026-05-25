import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameServer {
    public static final int PORT = 12345;

    // DB
    private static final String DB_URL = "jdbc:mysql://localhost:3306/userdb";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "DennyXD";

    // Game state
    private List<List<String>> questionPair = new ArrayList<>();
    private List<Integer> correctAnswers = new ArrayList<>();
    private int currentQuestionIdx = 0;
    private boolean gameStarted = false;
    private boolean gameOver = false;

    // Connected clients
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // Callback to update server GUI
    private Consumer<String> logCallback;
    private Runnable onClientConnected;
    private Runnable onQuestionAdvanced; // called whenever server moves to next question or game ends

    // Answer tracking per question
    private final Map<String, Integer> pendingAnswers = new ConcurrentHashMap<>(); // username -> answerIndex
    private final Set<String> answeredThisRound = ConcurrentHashMap.newKeySet();

    private ServerSocket serverSocket;

    public GameServer(Consumer<String> logCallback, Runnable onClientConnected, Runnable onQuestionAdvanced) {
        this.logCallback = logCallback;
        this.onClientConnected = onClientConnected;
        this.onQuestionAdvanced = onQuestionAdvanced;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(PORT);
        log("Server started on port " + PORT);
        Thread acceptThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket, this);
                    clients.add(handler);
                    new Thread(handler).start();
                    log("Client connected: " + socket.getInetAddress());
                    if (onClientConnected != null) onClientConnected.run();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) log("Accept error: " + e.getMessage());
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    // ---- Game Control (called by ServerFrame) ----

    public void loadQuestions(List<List<String>> questions, List<Integer> answers) {
        this.questionPair = questions;
        this.correctAnswers = answers;
        this.currentQuestionIdx = 0;
        this.gameOver = false;
    }

    public void startGame() {
        if (questionPair.isEmpty()) return;
        gameStarted = true;
        gameOver = false;
        currentQuestionIdx = 0;
        // Reset every connected client's score for the new game
        for (ClientHandler ch : clients) {
            ch.resetScore();
        }
        broadcast("GAME_RESET");
        broadcastCurrentQuestion();
    }

    public void nextQuestion() {
        // Mark anyone who hasn't answered as wrong (score stays, just move on)
        revealAnswerAndAdvance();
    }

    public int getCurrentQuestionIdx() { return currentQuestionIdx; }
    public int getTotalQuestions() { return questionPair.size(); }
    public List<List<String>> getQuestions() { return questionPair; }
    public List<Integer> getCorrectAnswers() { return correctAnswers; }
    public boolean isGameStarted() { return gameStarted; }
    public boolean isGameOver() { return gameOver; }
    public List<ClientHandler> getClients() { return clients; }

    public void broadcastCurrentQuestion() {
        if (currentQuestionIdx >= questionPair.size()) {
            endGame();
            return;
        }
        answeredThisRound.clear();
        List<String> q = questionPair.get(currentQuestionIdx);
        String msg = "QUESTION|" + currentQuestionIdx + "|" + questionPair.size() + "|"
                + q.get(0) + "|" + q.get(1) + "|" + q.get(2) + "|" + q.get(3) + "|" + q.get(4);
        broadcast(msg);
        log("Sent question " + (currentQuestionIdx + 1) + " to all clients.");
        if (onQuestionAdvanced != null) onQuestionAdvanced.run(); // notify server GUI to sync display
    }

    public void receiveAnswer(String username, int answerIdx) {
        if (answeredThisRound.contains(username)) return; // already answered
        answeredThisRound.add(username);

        int correct = correctAnswers.get(currentQuestionIdx);
        boolean isCorrect = (answerIdx == correct);
        // Update score for this client
        for (ClientHandler ch : clients) {
            if (ch.getUsername() != null && ch.getUsername().equals(username)) {
                if (isCorrect) ch.incrementScore();
                // Send result back to this client
                ch.send("ANSWER_RESULT|" + (isCorrect ? "CORRECT" : "WRONG") + "|" + correct);
                break;
            }
        }
        log(username + " answered " + (isCorrect ? "correctly" : "incorrectly"));

        // Check if all logged-in clients answered
        long loggedIn = clients.stream().filter(c -> c.getUsername() != null).count();
        if (answeredThisRound.size() >= loggedIn) {
            // Everyone answered, auto advance after brief pause
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            revealAnswerAndAdvance();
        }
    }

    private void revealAnswerAndAdvance() {
        if (gameOver) return;
        // Notify any unanswered clients they were wrong
        int correct = correctAnswers.get(currentQuestionIdx);
        for (ClientHandler ch : clients) {
            if (ch.getUsername() != null && !answeredThisRound.contains(ch.getUsername())) {
                ch.send("ANSWER_RESULT|WRONG|" + correct);
            }
        }
        currentQuestionIdx++;
        if (currentQuestionIdx >= questionPair.size()) {
            endGame();
        } else {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            broadcastCurrentQuestion();
        }
    }

    private void endGame() {
        gameOver = true;
        gameStarted = false;
        for (ClientHandler ch : clients) {
            if (ch.getUsername() != null) {
                saveScore(ch.getUsername(), ch.getScore());
            }
        }
        String leaderboard = getLeaderboardString();
        broadcast("GAME_OVER|" + leaderboard);
        log("Game over! Scores saved.");
        if (onQuestionAdvanced != null) onQuestionAdvanced.run(); // notify server GUI to show results
    }

    public String getLeaderboardString() {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT username, score FROM leaderboard ORDER BY score DESC, timestamp ASC LIMIT 10";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                sb.append(rs.getString("username")).append(":").append(rs.getInt("score")).append(";");
            }
        } catch (SQLException e) {
            log("DB leaderboard error: " + e.getMessage());
        }
        return sb.toString();
    }

    // ---- Auth ----

    public String registerUser(String username, String password) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Check duplicate
            PreparedStatement check = conn.prepareStatement("SELECT username FROM users WHERE username=?");
            check.setString(1, username);
            ResultSet rs = check.executeQuery();
            if (rs.next()) return "REGISTER_FAIL|Username already exists";
            PreparedStatement insert = conn.prepareStatement("INSERT INTO users(username,password) VALUES(?,?)");
            insert.setString(1, username);
            insert.setString(2, password);
            insert.executeUpdate();
            return "REGISTER_OK";
        } catch (SQLException e) {
            log("DB register error: " + e.getMessage());
            return "REGISTER_FAIL|Database error";
        }
    }

    public String loginUser(String username, String password) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE username=?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return "LOGIN_FAIL|Username not found";
            String storedPass = rs.getString("password");
            if (!storedPass.equals(password)) return "LOGIN_FAIL|Wrong password";
            // Check duplicate login
            for (ClientHandler ch : clients) {
                if (username.equals(ch.getUsername())) return "LOGIN_FAIL|Already logged in";
            }
            return "LOGIN_OK";
        } catch (SQLException e) {
            log("DB login error: " + e.getMessage());
            return "LOGIN_FAIL|Database error";
        }
    }

    private void saveScore(String username, int score) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO leaderboard(username,score) VALUES(?,?)");
            ps.setString(1, username);
            ps.setInt(2, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            log("DB save score error: " + e.getMessage());
        }
    }

    // ---- Utilities ----

    public void broadcast(String message) {
        for (ClientHandler ch : clients) {
            ch.send(message);
        }
    }

    public void removeClient(ClientHandler ch) {
        clients.remove(ch);
        log("Client disconnected: " + (ch.getUsername() != null ? ch.getUsername() : "unknown"));
        if (onClientConnected != null) onClientConnected.run();
    }

    private void log(String msg) {
        if (logCallback != null) logCallback.accept(msg);
        System.out.println("[Server] " + msg);
    }
}
